package com.zxcmc.exort.integration.worldedit;

import org.bukkit.configuration.ConfigurationSection;

public record WorldEditBulkConfig(
    boolean enabled,
    int bulkThresholdBlocks,
    int markerUpdatesPerTick,
    int refreshChunksPerTick,
    int busScanChunksPerTick,
    int networkStartsPerTick) {
  private static final String PATH = "performance.worldEditBulk.";

  public WorldEditBulkConfig {
    bulkThresholdBlocks = Math.max(1, bulkThresholdBlocks);
    markerUpdatesPerTick = Math.max(1, markerUpdatesPerTick);
    refreshChunksPerTick = Math.max(1, refreshChunksPerTick);
    busScanChunksPerTick = Math.max(1, busScanChunksPerTick);
    networkStartsPerTick = Math.max(1, networkStartsPerTick);
  }

  public static WorldEditBulkConfig fromConfig(ConfigurationSection config) {
    return new WorldEditBulkConfig(
        config.getBoolean(PATH + "enabled", true),
        config.getInt(PATH + "bulkThresholdBlocks", 512),
        config.getInt(PATH + "markerUpdatesPerTick", 1500),
        config.getInt(PATH + "refreshChunksPerTick", 2),
        config.getInt(PATH + "busScanChunksPerTick", 2),
        config.getInt(PATH + "networkStartsPerTick", 32));
  }
}
