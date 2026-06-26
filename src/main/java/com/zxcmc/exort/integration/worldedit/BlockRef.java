package com.zxcmc.exort.integration.worldedit;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

record BlockRef(UUID worldId, int x, int y, int z) {
  Block block() {
    World world = Bukkit.getWorld(worldId);
    if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
      return null;
    }
    return world.getBlockAt(x, y, z);
  }
}
