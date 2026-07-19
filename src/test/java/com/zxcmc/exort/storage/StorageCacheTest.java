package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.infra.db.DbItem;
import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.items.ItemStackCodec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class StorageCacheTest {
  @Test
  void flushSnapshotsFreezeTheirCollections() {
    List<DbItem> items = new ArrayList<>(List.of(new DbItem("stone", new byte[] {1}, 2L)));
    Set<String> removals = new HashSet<>(Set.of("dirt"));

    StorageCache.Snapshot snapshot = new StorageCache.Snapshot(1L, items);
    StorageCache.DeltaSnapshot delta = new StorageCache.DeltaSnapshot(2L, items, removals);
    items.clear();
    removals.clear();

    assertEquals(1, snapshot.items().size());
    assertEquals(1, delta.upserts().size());
    assertEquals(Set.of("dirt"), delta.removals());
    assertThrows(
        UnsupportedOperationException.class,
        () -> snapshot.items().add(new DbItem("new", new byte[] {2}, 1L)));
    assertThrows(UnsupportedOperationException.class, () -> delta.removals().add("new"));
  }

  @Test
  void decodeFailureKeepsOriginalBlobForQuarantine() {
    byte[] original = new byte[] {7, 8, 9};
    String key = ItemKeyUtil.sha256Hex(original);
    StoredItemCodec codec =
        new StoredItemCodec(
            new ItemStackCodec() {
              @Override
              public byte[] encode(ItemStack stack) {
                throw new AssertionError("encode must not run after decode failure");
              }

              @Override
              public ItemStack decode(byte[] bytes) {
                throw new IllegalArgumentException("broken payload");
              }
            });
    StorageCache cache = new StorageCache("storage", null, null, null, codec);

    StorageCache.LoadResult result = cache.loadFromDb(Map.of(key, new DbItem(key, original, 4L)));

    assertTrue(cache.isReadOnly());
    assertEquals(1, cache.corruptionSnapshot().size());
    assertTrue(cache.corruptionSnapshot().getFirst().reason().contains("DESERIALIZATION_FAILED"));
    assertEquals(1, result.quarantineEntries().size());
    assertArrayEquals(original, result.quarantineEntries().getFirst().originalBlob());
  }

  @Test
  void corruptLoadDegradesStorageAndBlocksEveryDirectMutationPrimitive() {
    byte[] blob = new byte[] {42};
    byte[] oversized = new byte[1_048_577];

    StorageCache cache = new StorageCache("storage", null, null, null);
    StorageCache.LoadResult loadResult =
        cache.loadFromDb(
            Map.of(
                "negative",
                new DbItem("negative", blob, -2),
                "empty",
                new DbItem("empty", new byte[0], 1),
                "oversized",
                new DbItem("oversized", oversized, 1)));

    assertTrue(cache.isLoaded());
    assertTrue(cache.isDegraded());
    assertTrue(cache.isReadOnly());
    assertTrue(cache.healthSummary().startsWith("DEGRADED_READ_ONLY corruptRows=3"));
    assertEquals(3, cache.corruptionSnapshot().size());
    assertEquals(3, loadResult.quarantineEntries().size());
    assertEquals(0, cache.totalAmount());
    assertEquals(0, cache.effectiveTotal());
    assertFalse(cache.isDirty());

    StorageCache.AddResult add = cache.tryAddItem(null, new PersistableItemStack(), 1L);
    assertFalse(add.accepted());
    assertEquals(StoredItemCodec.Failure.READ_ONLY, add.failure());
    assertEquals(0L, cache.removeItem("negative", 1L));
    assertTrue(cache.reserveItem("negative", 1L).isEmpty());
    assertTrue(cache.reserveAll(java.util.List.of(), null).isEmpty());
    assertEquals(0L, cache.removeMatchingWireless(null, null, 1L));
    assertEquals(0, cache.refreshCustomItems(null, null, true));
    assertEquals(0L, cache.totalAmount());
    assertFalse(cache.isDirty());
  }

  @Test
  void storageItemViewClonesOnlyWhenSampleCopyIsRequested() {
    CountingItemStack sample = new CountingItemStack();

    StorageCache.StorageItemView view =
        new StorageCache.StorageItemView("minecraft:stone", sample, 10L, 1L);

    assertEquals(0, sample.cloneCount());
    assertEquals("minecraft:stone", view.key());
    assertEquals(10L, view.amount());
    assertEquals(1L, view.weight());

    ItemStack copy = view.sampleCopy();

    assertEquals(1, sample.cloneCount());
    assertSame(sample.lastClone(), copy);
  }

  @Test
  void runtimeDbItemFailsWhenSampleCannotBeSerialized() {
    StorageCache cache = new StorageCache("storage", null, null, null);
    StorageCache.StorageItem item =
        new StorageCache.StorageItem(
            "minecraft:stone", new UnserializableItemStack(), 1L, 1L, null);

    assertThrows(IllegalStateException.class, () -> cache.runtimeDbItem(item));
  }

  @Test
  void rejectedNewItemDoesNotMutateCache() {
    StoredItemCodec codec =
        new StoredItemCodec(
            new ItemStackCodec() {
              @Override
              public byte[] encode(ItemStack stack) {
                throw new IllegalStateException("cannot serialize");
              }

              @Override
              public ItemStack decode(byte[] bytes) {
                throw new AssertionError("decode must not run");
              }
            });
    StorageCache cache = new StorageCache("storage", null, null, null, codec);

    StorageCache.AddResult result =
        cache.tryAddItem("minecraft:stone", new UnserializableItemStack(), 4L);

    assertEquals(StoredItemCodec.Failure.SERIALIZATION_FAILED, result.failure());
    assertEquals(0L, cache.totalAmount());
    assertEquals(0L, cache.effectiveTotal());
    assertTrue(cache.itemViewsSnapshot().isEmpty());
  }

  @Test
  void acceptedItemKeepsPreflightedBlobForFlush() {
    StoredItemCodec codec = persistableCodec();
    StorageCache cache = new StorageCache("storage", null, null, null, codec);

    StorageCache.AddResult result = cache.tryAddItem(null, new PersistableItemStack(), 3L);

    assertTrue(result.accepted());
    assertEquals(3L, cache.totalAmount());
    assertEquals(1, cache.snapshotItems().size());
    assertEquals(3, cache.snapshotItems().getFirst().blob().length);
  }

  @Test
  void stalePreflightCannotCommitAfterCacheMutation() {
    StorageCache cache = new StorageCache("storage", null, null, null, persistableCodec());
    StorageCache.PreparedAdd prepared = cache.prepareAdd(null, new PersistableItemStack(), 2L);
    assertTrue(prepared.accepted());

    assertTrue(cache.tryAddItem(null, new PersistableItemStack(), 1L).accepted());
    StorageCache.AddResult stale = cache.commitPrepared(prepared);

    assertEquals(StoredItemCodec.Failure.STALE_PREFLIGHT, stale.failure());
    assertEquals(1L, cache.totalAmount());
  }

  @Test
  void countOverflowIsRejectedWithoutChangingStoredAmount() {
    StorageCache cache = new StorageCache("storage", null, null, null, persistableCodec());
    StorageCache.AddResult initial =
        cache.tryAddItem(null, new PersistableItemStack(), Long.MAX_VALUE);
    assertTrue(initial.accepted());

    StorageCache.AddResult overflow =
        cache.tryAddItem(initial.key(), new PersistableItemStack(), 1L);

    assertEquals(StoredItemCodec.Failure.COUNT_OVERFLOW, overflow.failure());
    assertEquals(Long.MAX_VALUE, cache.totalAmount());
  }

  @Test
  void reservationRollbackCanOnlyBeAppliedOnce() {
    StorageCache cache = new StorageCache("storage", null, null, null, persistableCodec());
    StorageCache.AddResult added = cache.tryAddItem(null, new PersistableItemStack(), 5L);
    StorageCache.ReservedItem reserved = cache.reserveItem(added.key(), 5L).orElseThrow();

    cache.restoreReserved(reserved);
    cache.restoreReserved(reserved);

    assertEquals(5L, cache.totalAmount());
  }

  private static StoredItemCodec persistableCodec() {
    return new StoredItemCodec(
        new ItemStackCodec() {
          @Override
          public byte[] encode(ItemStack stack) {
            return new byte[] {7, 8, 9};
          }

          @Override
          public ItemStack decode(byte[] bytes) {
            return new PersistableItemStack();
          }
        });
  }

  private static final class CountingItemStack extends ItemStack {
    private int amount = 1;
    private int cloneCount;
    private ItemStack lastClone;

    @Override
    public Material getType() {
      return Material.STONE;
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public ItemStack clone() {
      cloneCount++;
      CountingItemStack clone = new CountingItemStack();
      clone.setAmount(amount);
      lastClone = clone;
      return lastClone;
    }

    int cloneCount() {
      return cloneCount;
    }

    ItemStack lastClone() {
      return lastClone;
    }
  }

  private static final class UnserializableItemStack extends ItemStack {
    @Override
    public Material getType() {
      return Material.STONE;
    }

    @Override
    public int getAmount() {
      return 1;
    }

    @Override
    public byte[] serializeAsBytes() {
      throw new IllegalStateException("cannot serialize");
    }
  }

  private static final class PersistableItemStack extends ItemStack {
    private int amount = 1;

    @Override
    public Material getType() {
      return Material.STONE;
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public boolean hasItemMeta() {
      return false;
    }

    @Override
    public ItemStack clone() {
      PersistableItemStack clone = new PersistableItemStack();
      clone.setAmount(amount);
      return clone;
    }
  }
}
