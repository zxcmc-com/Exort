package com.zxcmc.exort.storage;

import com.zxcmc.exort.gui.SortMode;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record StorageRuntimeConfig(
    String defaultSortModeName,
    int flushIntervalSeconds,
    long cacheIdleUnloadSeconds,
    long cacheIdleCheckSeconds) {
  public StorageRuntimeConfig {
    Objects.requireNonNull(defaultSortModeName, "defaultSortModeName");
  }

  public static StorageRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new StorageRuntimeConfig(
        SortMode.fromString(config.getString("defaultSortMode", SortMode.AMOUNT.name())).name(),
        config.getInt("flushIntervalSeconds", 10),
        config.getLong("cache.idleUnloadSeconds", 300),
        config.getLong("cache.idleCheckSeconds", 60));
  }
}
