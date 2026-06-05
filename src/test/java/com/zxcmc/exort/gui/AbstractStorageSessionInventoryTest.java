package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zxcmc.exort.items.ItemKeyUtil;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class AbstractStorageSessionInventoryTest {
  @Test
  void moveSampleToPlayerInventoryMergesThenFillsEmptySlotsInOnePass() {
    TestInventory inventory = new TestInventory();
    TestItemStack sample = new TestItemStack(Material.STONE, 1, 64, new byte[] {1});
    String key = ItemKeyUtil.keyFor(sample);
    inventory.slots[0] = new TestItemStack(Material.STONE, 60, 64, new byte[] {1});
    inventory.slots[1] = new TestItemStack(Material.DIRT, 64, 64, new byte[] {2});

    int moved =
        AbstractStorageSession.moveSampleToPlayerInventory(player(inventory), sample, key, 10);

    assertEquals(10, moved);
    assertEquals(64, inventory.slots[0].getAmount());
    assertEquals(Material.DIRT, inventory.slots[1].getType());
    assertEquals(6, inventory.slots[2].getAmount());
    assertNull(inventory.slots[36]);
  }

  @Test
  void moveSampleToPlayerInventoryReturnsPartialMovedCountForReservedRollback() {
    TestInventory inventory = new TestInventory();
    TestItemStack sample = new TestItemStack(Material.STONE, 1, 64, new byte[] {1});
    String key = ItemKeyUtil.keyFor(sample);
    Arrays.fill(inventory.slots, 1, 36, new TestItemStack(Material.STONE, 64, 64, new byte[] {1}));

    int moved =
        AbstractStorageSession.moveSampleToPlayerInventory(player(inventory), sample, key, 80);

    assertEquals(64, moved);
    assertEquals(64, inventory.slots[0].getAmount());
    assertNull(inventory.slots[36]);
  }

  private static Player player(TestInventory inventory) {
    return proxy(
        Player.class,
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "getInventory" -> inventory.proxy();
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "Player[test]";
            default -> defaultValue(method.getReturnType());
          };
        });
  }

  private static final class TestInventory {
    private final ItemStack[] slots = new ItemStack[41];
    private PlayerInventory proxy;

    PlayerInventory proxy() {
      if (proxy == null) {
        proxy =
            AbstractStorageSessionInventoryTest.proxy(
                PlayerInventory.class,
                (proxy, method, args) -> {
                  return switch (method.getName()) {
                    case "getItem" -> slots[(int) args[0]];
                    case "setItem" -> {
                      slots[(int) args[0]] = (ItemStack) args[1];
                      yield null;
                    }
                    case "firstEmpty" ->
                        throw new AssertionError(
                            "moveSampleToPlayerInventory must not call firstEmpty()");
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "PlayerInventory[test]";
                    default -> defaultValue(method.getReturnType());
                  };
                });
      }
      return proxy;
    }
  }

  private static final class TestItemStack extends ItemStack {
    private final Material type;
    private final int maxStackSize;
    private final byte[] serialized;
    private int amount;

    TestItemStack(Material type, int amount, int maxStackSize, byte[] serialized) {
      this.type = type;
      this.amount = amount;
      this.maxStackSize = maxStackSize;
      this.serialized = serialized.clone();
    }

    @Override
    public Material getType() {
      return type;
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
    public int getMaxStackSize() {
      return maxStackSize;
    }

    @Override
    public byte[] serializeAsBytes() {
      return serialized.clone();
    }

    @Override
    public ItemStack clone() {
      return new TestItemStack(type, amount, maxStackSize, serialized);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static Object defaultValue(Class<?> returnType) {
    if (returnType == Void.TYPE) {
      return null;
    }
    if (returnType == Boolean.TYPE) {
      return false;
    }
    if (returnType == Byte.TYPE) {
      return (byte) 0;
    }
    if (returnType == Short.TYPE) {
      return (short) 0;
    }
    if (returnType == Integer.TYPE) {
      return 0;
    }
    if (returnType == Long.TYPE) {
      return 0L;
    }
    if (returnType == Float.TYPE) {
      return 0.0f;
    }
    if (returnType == Double.TYPE) {
      return 0.0d;
    }
    if (returnType == Character.TYPE) {
      return '\0';
    }
    return null;
  }
}
