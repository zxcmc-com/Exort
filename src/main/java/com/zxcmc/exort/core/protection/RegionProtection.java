package com.zxcmc.exort.core.protection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface RegionProtection {
  boolean canBuild(Player player, Location location, Material material);

  boolean canBreak(Player player, Block block);

  boolean canInteract(Player player, Block block);

  boolean canUse(Player player, Block block);

  static RegionProtection allowAll() {
    return new RegionProtection() {
      @Override
      public boolean canBuild(Player player, Location location, Material material) {
        return true;
      }

      @Override
      public boolean canBreak(Player player, Block block) {
        return true;
      }

      @Override
      public boolean canInteract(Player player, Block block) {
        return true;
      }

      @Override
      public boolean canUse(Player player, Block block) {
        return true;
      }
    };
  }
}
