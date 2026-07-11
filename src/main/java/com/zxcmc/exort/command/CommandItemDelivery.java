package com.zxcmc.exort.command;

import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class CommandItemDelivery {
  private CommandItemDelivery() {}

  record Result(int inventory, int dropped, int undelivered) {
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
    int undelivered = 0;
    while (remaining > 0) {
      ItemStack prototype = itemFactory.get();
      if (isEmpty(prototype)) {
        undelivered += remaining;
        break;
      }
      int stackSize = Math.max(1, prototype.getMaxStackSize());
      int move = Math.min(stackSize, remaining);
      ItemStack stack = prototype.clone();
      stack.setAmount(move);
      Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
      long reportedLeftover =
          leftovers.values().stream()
              .filter(leftover -> leftover != null)
              .mapToLong(ItemStack::getAmount)
              .sum();
      int leftoverAmount = (int) Math.min(move, Math.max(0L, reportedLeftover));
      inventory += move - leftoverAmount;
      int failedDrop = 0;
      int dropBudget = leftoverAmount;
      for (ItemStack leftover : leftovers.values()) {
        if (isEmpty(leftover) || dropBudget <= 0) {
          continue;
        }
        int dropAmount = Math.min(dropBudget, leftover.getAmount());
        ItemStack safeLeftover = leftover.clone();
        safeLeftover.setAmount(dropAmount);
        try {
          target.getWorld().dropItemNaturally(target.getLocation(), safeLeftover);
          dropped += dropAmount;
        } catch (RuntimeException failure) {
          failedDrop += dropAmount;
        }
        dropBudget -= dropAmount;
      }
      remaining -= move;
      int failedDelivery = failedDrop + dropBudget;
      if (failedDelivery > 0) {
        undelivered += failedDelivery + remaining;
        break;
      }
    }
    return new Result(inventory, dropped, undelivered);
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getAmount() <= 0 || stack.getType() == Material.AIR;
  }
}
