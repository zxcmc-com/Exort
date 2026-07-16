package com.zxcmc.exort.chunkloader;

import com.zxcmc.exort.infra.config.ConfigNumbers;
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
    if (config == null) {
      return new ChunkLoaderConfig(
          DEFAULT_ENABLED,
          DEFAULT_RADIUS,
          ChunkLoaderLimits.defaults(),
          ChunkLoaderAuditConfig.fromConfig(null));
    }
    return fromNumbers(config, new ConfigNumbers(config, logger));
  }

  public static ChunkLoaderConfig fromNumbers(ConfigurationSection config, ConfigNumbers numbers) {
    boolean enabled =
        config == null
            ? DEFAULT_ENABLED
            : config.getBoolean("chunkLoader.enabled", DEFAULT_ENABLED);
    return new ChunkLoaderConfig(
        enabled,
        numbers.integer("chunkLoader.radius", DEFAULT_RADIUS, MIN_RADIUS, MAX_RADIUS),
        ChunkLoaderLimits.fromConfig(numbers),
        ChunkLoaderAuditConfig.fromNumbers(config, numbers));
  }

  static int clampRadius(int raw) {
    return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, raw));
  }
}
