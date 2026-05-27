package com.zxcmc.exort.placement;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

public record BlockKey(UUID worldId, int x, int y, int z) {
  static BlockKey of(Block block) {
    return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
  }

  Block resolve() {
    World world = Bukkit.getWorld(worldId);
    if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
      return null;
    }
    return world.getBlockAt(x, y, z);
  }
}
