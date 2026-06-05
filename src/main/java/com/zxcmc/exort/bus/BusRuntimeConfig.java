package com.zxcmc.exort.bus;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record BusRuntimeConfig(
    int activeIntervalTicks,
    int idleIntervalTicks,
    int itemsPerOperation,
    int maxOperationsPerTick,
    int maxOperationsPerChunk,
    boolean allowStorageTargets,
    BusMode defaultImportMode,
    BusMode defaultExportMode) {
  public BusRuntimeConfig {
    defaultImportMode = defaultImportMode == null ? BusMode.WHITELIST : defaultImportMode;
    defaultExportMode = defaultExportMode == null ? BusMode.WHITELIST : defaultExportMode;
  }

  public static BusRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new BusRuntimeConfig(
        Math.max(1, config.getInt("bus.activeIntervalTicks", 5)),
        Math.max(1, config.getInt("bus.idleIntervalTicks", 40)),
        Math.max(1, config.getInt("bus.itemsPerOperation", 1)),
        Math.max(1, config.getInt("performance.bus.maxOperationsPerTick", 500)),
        Math.max(0, config.getInt("performance.bus.maxOperationsPerChunk", 40)),
        config.getBoolean("bus.allowStorageTargets", true),
        readMode(config, "bus.defaultMode.import"),
        readMode(config, "bus.defaultMode.export"));
  }

  public BusMode defaultMode(BusType type) {
    return type == BusType.EXPORT ? defaultExportMode : defaultImportMode;
  }

  public BusMode defaultMode(boolean exportBus) {
    return exportBus ? defaultExportMode : defaultImportMode;
  }

  private static BusMode readMode(ConfigurationSection config, String path) {
    BusMode mode = BusMode.fromString(config.getString(path, "WHITELIST"));
    return mode == null ? BusMode.WHITELIST : mode;
  }
}
