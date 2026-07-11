package com.zxcmc.exort.chunkloader;

import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public record ChunkLoaderConfig(
    boolean enabled, int radius, ChunkLoaderLimits limits, ChunkLoaderAuditConfig audit) {
  public static final boolean DEFAULT_ENABLED = true;
  public static final int DEFAULT_RADIUS = 1;
  public static final int MIN_RADIUS = 0;
  public static final int MAX_RADIUS = 8;

  public ChunkLoaderConfig {
    radius = clampRadius(radius);
    limits = limits == null ? ChunkLoaderLimits.defaults() : limits;
    audit = audit == null ? ChunkLoaderAuditConfig.fromConfig(null) : audit;
  }

  public static ChunkLoaderConfig fromConfig(ConfigurationSection config, Logger logger) {
    boolean enabled =
        config == null
            ? DEFAULT_ENABLED
            : config.getBoolean("chunkLoader.enabled", DEFAULT_ENABLED);
    int raw = config == null ? DEFAULT_RADIUS : config.getInt("chunkLoader.radius", DEFAULT_RADIUS);
    int radius = clampRadius(raw);
    if (raw != radius && logger != null) {
      logger.warning(
          "chunkLoader.radius="
              + raw
              + " is outside "
              + MIN_RADIUS
              + ".."
              + MAX_RADIUS
              + "; using "
              + radius
              + ".");
    }
    return new ChunkLoaderConfig(
        enabled,
        radius,
        ChunkLoaderLimits.fromConfig(config, logger),
        ChunkLoaderAuditConfig.fromConfig(config, logger));
  }

  static int clampRadius(int raw) {
    return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, raw));
  }
}
