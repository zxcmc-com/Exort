package com.zxcmc.exort.integration.protection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class MutableRegionProtectionTest {
  @Test
  void delegatesToLatestProtectionWithoutReplacingHolder() {
    MutableRegionProtection holder = new MutableRegionProtection(fixed(true));

    assertTrue(holder.canBuild(null, null, null));

    holder.setDelegate(fixed(false));

    assertFalse(holder.canBuild(null, null, null));
  }

  private static RegionProtection fixed(boolean allowed) {
    return new RegionProtection() {
      @Override
      public boolean canBuild(Player player, Location location, Material material) {
        return allowed;
      }

      @Override
      public boolean canBreak(Player player, Block block) {
        return allowed;
      }

      @Override
      public boolean canInteract(Player player, Block block) {
        return allowed;
      }

      @Override
      public boolean canUse(Player player, Block block) {
        return allowed;
      }
    };
  }
}
