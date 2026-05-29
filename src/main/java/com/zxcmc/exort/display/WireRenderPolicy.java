package com.zxcmc.exort.display;

import com.zxcmc.exort.debug.PerfStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class WireRenderPolicy {
  private final WireRenderMode configuredMode;
  private final WireAutoRenderConfig config;
  private final WireBlockIndex index;
  private final Map<ChunkKey, Boolean> compactZones = new HashMap<>();

  public WireRenderPolicy(
      WireRenderMode configuredMode, WireAutoRenderConfig config, WireBlockIndex index) {
    this.configuredMode = configuredMode == null ? WireRenderMode.AUTO : configuredMode;
    this.config = config;
    this.index = index;
  }

  public WireAutoRenderConfig config() {
    return config;
  }

  public boolean auto() {
    return configuredMode == WireRenderMode.AUTO;
  }

  public WireRenderMode desiredMode(Block wire) {
    if (configuredMode == WireRenderMode.COMPACT || configuredMode == WireRenderMode.DETAILED) {
      return configuredMode;
    }
    if (wire == null || wire.getWorld() == null) {
      return WireRenderMode.DETAILED;
    }
    ChunkKey key = ChunkKey.of(wire);
    boolean compact = compactZones.getOrDefault(key, false);
    int wires = index.countAround(wire, config.chunkRadius());
    if (!compact && wires >= config.enterCompactWires()) {
      compact = true;
    } else if (compact && wires <= config.exitCompactWires()) {
      compact = false;
    }
    compactZones.put(key, compact);
    PerfStats.setGauge("wire.autoRender.zoneWires", wires);
    return compact ? WireRenderMode.COMPACT : WireRenderMode.DETAILED;
  }

  public boolean hasNearbyPlayers(Block block) {
    if (!auto() || block == null || block.getWorld() == null) {
      return false;
    }
    double radius = config.idlePlayerRadiusBlocks();
    if (radius <= 0.0) {
      return false;
    }
    double radiusSquared = radius * radius;
    Location center = block.getLocation().add(0.5, 0.5, 0.5);
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!player.getWorld().equals(block.getWorld())) {
        continue;
      }
      if (player.getLocation().distanceSquared(center) <= radiusSquared) {
        return true;
      }
    }
    return false;
  }

  private record ChunkKey(UUID worldId, int x, int z) {
    static ChunkKey of(Block block) {
      return new ChunkKey(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    }
  }
}
