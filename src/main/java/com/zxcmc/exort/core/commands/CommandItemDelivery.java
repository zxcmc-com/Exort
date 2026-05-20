package com.zxcmc.exort.core.commands;

import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class CommandItemDelivery {
  private CommandItemDelivery() {}

  static int clampAmount(int amount, int maxAmount) {
    return Math.max(1, Math.min(maxAmount, amount));
  }

  static int deliver(Player target, Supplier<ItemStack> itemFactory, int amount) {
    int remaining = Math.max(0, amount);
    int delivered = 0;
    while (remaining > 0) {
      ItemStack prototype = itemFactory.get();
      if (prototype == null || prototype.getType().isAir()) break;
      int stackSize = Math.max(1, prototype.getMaxStackSize());
      int move = Math.min(stackSize, remaining);
      ItemStack stack = prototype.clone();
      stack.setAmount(move);
      Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
      int leftoverAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
      delivered += move - leftoverAmount;
      if (leftoverAmount > 0) break;
      remaining -= move;
    }
    return delivered;
  }
}
