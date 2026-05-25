package com.zxcmc.exort.core.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class CommandItemDeliveryTest {
  @Test
  void clampAmountKeepsGiveRequestsInSupportedRange() {
    assertEquals(1, CommandItemDelivery.clampAmount(0, 512));
    assertEquals(64, CommandItemDelivery.clampAmount(64, 512));
    assertEquals(512, CommandItemDelivery.clampAmount(1_000, 512));
  }

  @Test
  void deliverPutsEverythingInInventoryWhenSpaceAllows() {
    DeliveryHarness harness = deliveryHarness(128);

    CommandItemDelivery.Result result =
        CommandItemDelivery.deliver(harness.player(), () -> item(1), 70);

    assertEquals(70, result.inventory());
    assertEquals(0, result.dropped());
    assertEquals(70, result.total());
    assertEquals(70, harness.inventoryAccepted());
    assertEquals(0, harness.droppedAmount());
  }

  @Test
  void deliverDropsLeftoversAndContinuesAfterFirstFullStack() {
    DeliveryHarness harness = deliveryHarness(10);

    CommandItemDelivery.Result result =
        CommandItemDelivery.deliver(harness.player(), () -> item(1), 70);

    assertEquals(10, result.inventory());
    assertEquals(60, result.dropped());
    assertEquals(70, result.total());
    assertEquals(10, harness.inventoryAccepted());
    assertEquals(60, harness.droppedAmount());
    assertEquals(2, harness.drops().size());
  }

  @Test
  void deliverDropsEverythingWhenInventoryIsFull() {
    DeliveryHarness harness = deliveryHarness(0);

    CommandItemDelivery.Result result =
        CommandItemDelivery.deliver(harness.player(), () -> item(1), 130);

    assertEquals(0, result.inventory());
    assertEquals(130, result.dropped());
    assertEquals(130, result.total());
    assertEquals(0, harness.inventoryAccepted());
    assertEquals(130, harness.droppedAmount());
    assertEquals(List.of(64, 64, 2), harness.dropAmounts());
  }

  private static DeliveryHarness deliveryHarness(int inventoryCapacity) {
    List<ItemStack> drops = new ArrayList<>();
    World world =
        (World)
            Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] {World.class},
                (proxy, method, args) -> {
                  return switch (method.getName()) {
                    case "dropItemNaturally" -> {
                      ItemStack item = (ItemStack) args[1];
                      drops.add(item.clone());
                      yield null;
                    }
                    case "getName" -> "test";
                    case "toString" -> "world(test)";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                  };
                });
    Location location = new Location(world, 1.0, 2.0, 3.0);
    InventoryState inventory = new InventoryState(inventoryCapacity);
    PlayerInventory playerInventory =
        (PlayerInventory)
            Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(),
                new Class<?>[] {PlayerInventory.class},
                (proxy, method, args) -> {
                  return switch (method.getName()) {
                    case "addItem" -> inventory.add((ItemStack[]) args[0]);
                    case "toString" -> "player-inventory";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                  };
                });
    Player player =
        (Player)
            Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                  return switch (method.getName()) {
                    case "getInventory" -> playerInventory;
                    case "getWorld" -> world;
                    case "getLocation" -> location;
                    case "getName" -> "Target";
                    case "toString" -> "player(Target)";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                  };
                });
    return new DeliveryHarness(player, inventory, drops);
  }

  private static ItemStack item(int amount) {
    return new TestItemStack(amount);
  }

  private record DeliveryHarness(Player player, InventoryState inventory, List<ItemStack> drops) {
    int inventoryAccepted() {
      return inventory.accepted();
    }

    int droppedAmount() {
      return drops.stream().mapToInt(ItemStack::getAmount).sum();
    }

    List<Integer> dropAmounts() {
      return drops.stream().map(ItemStack::getAmount).toList();
    }
  }

  private static final class InventoryState {
    private int capacity;
    private int accepted;

    InventoryState(int capacity) {
      this.capacity = Math.max(0, capacity);
    }

    Map<Integer, ItemStack> add(ItemStack[] items) {
      Map<Integer, ItemStack> leftovers = new HashMap<>();
      for (int i = 0; i < items.length; i++) {
        ItemStack item = items[i];
        int move = Math.min(capacity, item.getAmount());
        capacity -= move;
        accepted += move;
        int leftover = item.getAmount() - move;
        if (leftover > 0) {
          ItemStack leftoverStack = item.clone();
          leftoverStack.setAmount(leftover);
          leftovers.put(i, leftoverStack);
        }
      }
      return leftovers;
    }

    int accepted() {
      return accepted;
    }
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
    public int getMaxStackSize() {
      return 64;
    }

    @Override
    public ItemStack clone() {
      return new TestItemStack(amount);
    }
  }
}
