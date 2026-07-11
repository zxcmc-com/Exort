package com.zxcmc.exort.storage;

import com.zxcmc.exort.gui.SortMode;
import com.zxcmc.exort.infra.config.ConfigEnums;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record StorageRuntimeConfig(
    String defaultSortModeName,
    int flushIntervalSeconds,
    long cacheIdleUnloadSeconds,
    long cacheIdleCheckSeconds) {
  static final int MAX_SCHEDULER_INTERVAL_SECONDS = Integer.MAX_VALUE / 20;

  public StorageRuntimeConfig {
    Objects.requireNonNull(defaultSortModeName, "defaultSortModeName");
  }

  public static StorageRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new StorageRuntimeConfig(
        ConfigEnums.parse("defaultSortMode", config.getString("defaultSortMode"), SortMode.AMOUNT)
            .name(),
        clampSeconds(config.getLong("performance.storage.flushIntervalSeconds", 10)),
        clampSeconds(config.getLong("performance.storage.idleUnloadSeconds", 300)),
        clampSeconds(config.getLong("performance.storage.idleCheckSeconds", 60)));
  }

  private static int clampSeconds(long value) {
    return (int) Math.max(0L, Math.min(MAX_SCHEDULER_INTERVAL_SECONDS, value));
  }
}
