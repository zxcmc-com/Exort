package com.zxcmc.exort.integration.worldedit;

import org.bukkit.configuration.ConfigurationSection;

public record WorldEditBulkConfig(
    boolean enabled,
    int bulkThresholdBlocks,
    int markerUpdatesPerTick,
    int refreshChunksPerTick,
    int busScanChunksPerTick,
    int networkStartsPerTick) {
  private static final String PATH = "performance.worldEditBulk";

  public WorldEditBulkConfig {
    bulkThresholdBlocks = Math.max(1, bulkThresholdBlocks);
    markerUpdatesPerTick = Math.max(1, markerUpdatesPerTick);
    refreshChunksPerTick = Math.max(1, refreshChunksPerTick);
    busScanChunksPerTick = Math.max(1, busScanChunksPerTick);
    networkStartsPerTick = Math.max(1, networkStartsPerTick);
  }

  public static WorldEditBulkConfig fromConfig(ConfigurationSection config) {
    return defaults(config.getBoolean(PATH, true));
  }

  public static WorldEditBulkConfig defaults(boolean enabled) {
    return new WorldEditBulkConfig(enabled, 512, 1500, 2, 2, 32);
  }
}
