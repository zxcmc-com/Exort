package com.zxcmc.exort.placement;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

public final class RecentPlacementTracker {
  private final Map<BlockPos, Integer> recentPlacements = new ConcurrentHashMap<>();

  public void markPlaced(Block block) {
    if (block == null) return;
    int expiresAt = Bukkit.getCurrentTick() + 2;
    recentPlacements.put(BlockPos.of(block), expiresAt);
  }

  public boolean isRecentlyPlaced(Block block) {
    if (block == null) return false;
    BlockPos pos = BlockPos.of(block);
    Integer expiresAt = recentPlacements.get(pos);
    if (expiresAt == null) {
      return false;
    }
    if (Bukkit.getCurrentTick() <= expiresAt) {
      return true;
    }
    recentPlacements.remove(pos);
    return false;
  }

  private record BlockPos(UUID world, int x, int y, int z) {
    static BlockPos of(Block block) {
      return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
