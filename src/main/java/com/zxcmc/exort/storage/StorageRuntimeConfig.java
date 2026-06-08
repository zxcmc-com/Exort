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
  public StorageRuntimeConfig {
    Objects.requireNonNull(defaultSortModeName, "defaultSortModeName");
  }

  public static StorageRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new StorageRuntimeConfig(
        ConfigEnums.parse("defaultSortMode", config.getString("defaultSortMode"), SortMode.AMOUNT)
            .name(),
        config.getInt("performance.storage.flushIntervalSeconds", 10),
        config.getLong("performance.storage.idleUnloadSeconds", 300),
        config.getLong("performance.storage.idleCheckSeconds", 60));
  }
}
