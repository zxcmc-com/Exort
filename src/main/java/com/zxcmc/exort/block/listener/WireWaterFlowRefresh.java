package com.zxcmc.exort.block.listener;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

final class WireWaterFlowRefresh {
  private static final BlockFace[] FACES =
      new BlockFace[] {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
      };

  private WireWaterFlowRefresh() {}

  static void refreshAfterWirePlacement(Block wireBlock, Material replacedType) {
    if (wireBlock == null || wireBlock.getWorld() == null) return;
    boolean replacedWater = replacedType == Material.WATER;
    boolean adjacentWater = false;
    for (BlockFace face : FACES) {
      Block neighbor = wireBlock.getRelative(face);
      if (!isLoaded(neighbor) || neighbor.getType() != Material.WATER) {
        continue;
      }
      adjacentWater = true;
      refreshWater(neighbor);
    }
    if (replacedWater || adjacentWater) {
      wireBlock.getState().update(true, false);
    }
  }

  private static void refreshWater(Block water) {
    try {
      water.fluidTick();
    } catch (NoSuchMethodError ignored) {
      // Paper has fluidTick on the current target API; keep the fallback harmless on older APIs.
    }
    water.getState().update(true, true);
  }

  private static boolean isLoaded(Block block) {
    if (block == null) return false;
    World world = block.getWorld();
    return world != null && world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }
}
