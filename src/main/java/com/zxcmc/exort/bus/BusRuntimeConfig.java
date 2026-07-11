package com.zxcmc.exort.bus;

import com.zxcmc.exort.infra.config.ConfigEnums;
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
  static final int MAX_OPERATIONS_PER_TICK = 4_096;

  public BusRuntimeConfig {
    defaultImportMode = defaultImportMode == null ? BusMode.WHITELIST : defaultImportMode;
    defaultExportMode = defaultExportMode == null ? BusMode.WHITELIST : defaultExportMode;
  }

  public static BusRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    int maxOperationsPerTick =
        clamp(
            config.getInt("performance.bus.maxOperationsPerTick", 500), 1, MAX_OPERATIONS_PER_TICK);
    return new BusRuntimeConfig(
        Math.max(1, config.getInt("bus.activeIntervalTicks", 5)),
        Math.max(1, config.getInt("bus.idleIntervalTicks", 40)),
        Math.max(1, config.getInt("bus.itemsPerOperation", 1)),
        maxOperationsPerTick,
        clamp(config.getInt("performance.bus.maxOperationsPerChunk", 40), 0, maxOperationsPerTick),
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
    return ConfigEnums.parse(path, config.getString(path), BusMode.WHITELIST);
  }

  private static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }
}
