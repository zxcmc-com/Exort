package com.zxcmc.exort.monitor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

public final class MonitorPlacementTracker {
  private final Map<MonitorPos, Integer> recentPlacements = new ConcurrentHashMap<>();

  public void markPlaced(Block block) {
    if (block == null) return;
    int expiresAt = Bukkit.getCurrentTick() + 2;
    recentPlacements.put(MonitorPos.of(block), expiresAt);
  }

  public boolean isRecentlyPlaced(Block block) {
    if (block == null) return false;
    MonitorPos pos = MonitorPos.of(block);
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

  private record MonitorPos(UUID world, int x, int y, int z) {
    static MonitorPos of(Block block) {
      return new MonitorPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
