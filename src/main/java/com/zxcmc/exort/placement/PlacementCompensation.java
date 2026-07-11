package com.zxcmc.exort.placement;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Restores a placement's replaced block and conservatively returns one consumed item. */
public final class PlacementCompensation {
  private PlacementCompensation() {}

  public static void restoreAndRefund(
      Block block,
      BlockState replacedState,
      Player player,
      ItemStack refund,
      boolean shouldRefund) {
    if (replacedState != null) {
      replacedState.update(true, false);
    } else if (block != null) {
      block.setType(Material.AIR, false);
    }
    if (!shouldRefund || refund == null || refund.getType() == Material.AIR) {
      return;
    }
    ItemStack one = refund.clone();
    one.setAmount(1);
    if (player != null && player.isOnline()) {
      Map<Integer, ItemStack> leftovers = player.getInventory().addItem(one);
      for (ItemStack leftover : leftovers.values()) {
        if (leftover != null && leftover.getType() != Material.AIR && leftover.getAmount() > 0) {
          player.getWorld().dropItemNaturally(player.getLocation(), leftover.clone());
        }
      }
      player.updateInventory();
      return;
    }
    if (block != null && block.getWorld() != null) {
      block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), one);
    }
  }
}
