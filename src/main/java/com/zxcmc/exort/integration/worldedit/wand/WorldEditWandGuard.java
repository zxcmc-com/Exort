package com.zxcmc.exort.integration.worldedit.wand;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface WorldEditWandGuard {
  WorldEditWandGuard ALLOW = (player, item) -> false;

  boolean isWorldEditWand(Player player, ItemStack item);
}
