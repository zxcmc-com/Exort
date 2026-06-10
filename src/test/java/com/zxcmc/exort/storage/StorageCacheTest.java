package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.infra.db.DbItem;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class StorageCacheTest {
  @Test
  void loadFromDbSkipsRowsThatAreInvalidBeforeItemDeserialization() {
    byte[] blob = new byte[] {42};
    byte[] oversized = new byte[1_048_577];

    StorageCache cache = new StorageCache("storage", null, null, null);
    cache.loadFromDb(
        Map.of(
            "negative",
            new DbItem("negative", blob, -2),
            "empty",
            new DbItem("empty", new byte[0], 1),
            "oversized",
            new DbItem("oversized", oversized, 1)));

    assertTrue(cache.isLoaded());
    assertEquals(0, cache.totalAmount());
    assertEquals(0, cache.effectiveTotal());
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
}
