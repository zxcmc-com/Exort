package com.zxcmc.exort.core.commands;

import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class CommandItemDelivery {
  private CommandItemDelivery() {}

  record Result(int inventory, int dropped) {
    int total() {
      return inventory + dropped;
    }
  }

  static int clampAmount(int amount, int maxAmount) {
    return Math.max(1, Math.min(maxAmount, amount));
  }

  static Result deliver(Player target, Supplier<ItemStack> itemFactory, int amount) {
    int remaining = Math.max(0, amount);
    int inventory = 0;
    int dropped = 0;
    while (remaining > 0) {
      ItemStack prototype = itemFactory.get();
      if (isEmpty(prototype)) break;
      int stackSize = Math.max(1, prototype.getMaxStackSize());
      int move = Math.min(stackSize, remaining);
      ItemStack stack = prototype.clone();
      stack.setAmount(move);
      Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
      int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
      inventory += move - leftoverAmount;
      for (ItemStack leftover : leftovers.values()) {
        if (isEmpty(leftover)) {
          continue;
        }
        target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        dropped += leftover.getAmount();
      }
      remaining -= move;
    }
    return new Result(inventory, dropped);
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getAmount() <= 0 || stack.getType() == Material.AIR;
  }
}
