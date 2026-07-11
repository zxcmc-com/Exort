package com.zxcmc.exort.bus.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.infra.db.DbItem;
import com.zxcmc.exort.items.ItemStackCodec;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StoredItemCodec;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class BusItemTransferTest {
  @Test
  void importIntoDegradedStorageDoesNotStartSourceMutation() {
    StorageCache destination = cache(codec());
    destination.loadFromDb(Map.of("corrupt", new DbItem("corrupt", new byte[] {1}, -1L)));
    AtomicBoolean sourceCommit = new AtomicBoolean();
    AtomicBoolean sourceRollback = new AtomicBoolean();

    boolean moved =
        BusItemTransfer.importItem(
            destination,
            null,
            new TestItemStack(1),
            1,
            () -> sourceCommit.set(true),
            () -> sourceRollback.set(true));

    assertFalse(moved);
    assertFalse(sourceCommit.get());
    assertFalse(sourceRollback.get());
    assertTrue(destination.isReadOnly());
    assertEquals(0L, destination.totalAmount());
  }

  @Test
  void importRejectsDestinationBeforeMutatingSource() {
    StorageCache destination = cache(failingCodec());
    AtomicBoolean sourceMutated = new AtomicBoolean();

    boolean moved =
        BusItemTransfer.importItem(
            destination,
            "item-key",
            new TestItemStack(1),
            4,
            () -> sourceMutated.set(true),
            () -> sourceMutated.set(false));

    assertFalse(moved);
    assertFalse(sourceMutated.get());
    assertEquals(0L, destination.totalAmount());
  }

  @Test
  void importRollsBackPartiallyAppliedSourceMutationWhenItThrows() {
    StorageCache destination = cache(codec());
    AtomicInteger source = new AtomicInteger(4);

    assertThrows(
        IllegalStateException.class,
        () ->
            BusItemTransfer.importItem(
                destination,
                null,
                new TestItemStack(1),
                4,
                () -> {
                  source.set(0);
                  throw new IllegalStateException("source mutation failed");
                },
                () -> source.set(4)));

    assertEquals(4, source.get());
    assertEquals(0L, destination.totalAmount());
  }

  @Test
  void exportRestoresReservationWhenDestinationThrows() {
    StorageCache source = cache(codec());
    StorageCache.AddResult added = source.tryAddItem(null, new TestItemStack(1), 7L);
    assertTrue(added.accepted());

    assertThrows(
        IllegalStateException.class,
        () ->
            BusItemTransfer.exportItem(
                source,
                added.key(),
                5,
                (sample, amount) -> {
                  throw new IllegalStateException("destination failed");
                }));

    assertEquals(7L, source.getAmount(added.key()));
  }

  @Test
  void storageTransferPreservesSourceWhenDestinationPreflightFails() {
    StorageCache source = cache(codec());
    StorageCache destination = cache(failingCodec());
    StorageCache.AddResult added = source.tryAddItem(null, new TestItemStack(1), 7L);
    assertTrue(added.accepted());

    int moved =
        BusItemTransfer.transferStorage(source, destination, added.key(), new TestItemStack(1), 5);

    assertEquals(0, moved);
    assertEquals(7L, source.getAmount(added.key()));
    assertEquals(0L, destination.totalAmount());
  }

  @Test
  void successfulStorageTransferConservesExactItemCount() {
    StorageCache source = cache(codec());
    StorageCache destination = cache(codec());
    StorageCache.AddResult added = source.tryAddItem(null, new TestItemStack(1), 7L);
    assertTrue(added.accepted());

    int moved =
        BusItemTransfer.transferStorage(source, destination, added.key(), new TestItemStack(1), 5);

    assertEquals(5, moved);
    assertEquals(2L, source.getAmount(added.key()));
    assertEquals(5L, destination.getAmount(added.key()));
  }

  @Test
  void inventoryInsertionRestoresTouchedSlotsWhenMutationThrows() {
    Map<Integer, ItemStack> slots = new HashMap<>();
    AtomicBoolean failFirstWrite = new AtomicBoolean(true);
    Inventory inventory =
        (Inventory)
            Proxy.newProxyInstance(
                Inventory.class.getClassLoader(),
                new Class<?>[] {Inventory.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getItem" -> slots.get((int) args[0]);
                      case "setItem" -> {
                        int slot = (int) args[0];
                        ItemStack stack = (ItemStack) args[1];
                        if (stack == null) {
                          slots.remove(slot);
                        } else {
                          slots.put(slot, stack.clone());
                        }
                        if (failFirstWrite.getAndSet(false)) {
                          throw new IllegalStateException("partial inventory write");
                        }
                        yield null;
                      }
                      case "toString" -> "throwing-inventory";
                      case "hashCode" -> System.identityHashCode(proxy);
                      case "equals" -> proxy == args[0];
                      default -> throw new UnsupportedOperationException(method.toString());
                    });

    assertThrows(
        IllegalStateException.class,
        () ->
            BusEngine.mutateInventoryAtomic(
                inventory,
                new int[] {0},
                () -> {
                  inventory.setItem(0, new TestItemStack(1));
                  return 1;
                }));

    assertTrue(slots.isEmpty());
  }

  private static StorageCache cache(StoredItemCodec codec) {
    return new StorageCache("storage", null, null, null, codec);
  }

  private static StoredItemCodec codec() {
    return new StoredItemCodec(
        new ItemStackCodec() {
          @Override
          public byte[] encode(ItemStack stack) {
            return new byte[] {1};
          }

          @Override
          public ItemStack decode(byte[] bytes) {
            return new TestItemStack(1);
          }
        });
  }

  private static StoredItemCodec failingCodec() {
    return new StoredItemCodec(
        new ItemStackCodec() {
          @Override
          public byte[] encode(ItemStack stack) {
            throw new IllegalStateException("encode failed");
          }

          @Override
          public ItemStack decode(byte[] bytes) {
            throw new AssertionError("decode must not run");
          }
        });
  }

  private static final class TestItemStack extends ItemStack {
    private int amount;

    TestItemStack(int amount) {
      this.amount = amount;
    }

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
      return new TestItemStack(amount);
    }
  }
}
