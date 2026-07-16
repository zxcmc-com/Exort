package com.zxcmc.exort.bus;

import com.zxcmc.exort.infra.config.ConfigEnums;
import com.zxcmc.exort.infra.config.ConfigNumbers;
import java.util.Objects;
import java.util.logging.Logger;
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
  static final int MAX_INTERVAL_TICKS = 72_000;
  static final int MAX_ITEMS_PER_OPERATION = 64;

  public BusRuntimeConfig {
    defaultImportMode = defaultImportMode == null ? BusMode.WHITELIST : defaultImportMode;
    defaultExportMode = defaultExportMode == null ? BusMode.WHITELIST : defaultExportMode;
  }

  public static BusRuntimeConfig fromConfig(ConfigurationSection config) {
    return fromConfig(config, (Logger) null);
  }

  public static BusRuntimeConfig fromConfig(ConfigurationSection config, Logger logger) {
    Objects.requireNonNull(config, "config");
    return fromNumbers(config, new ConfigNumbers(config, logger));
  }

  public static BusRuntimeConfig fromNumbers(ConfigurationSection config, ConfigNumbers numbers) {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(numbers, "numbers");
    int maxOperationsPerTick =
        numbers.integer("performance.bus.maxOperationsPerTick", 500, 1, MAX_OPERATIONS_PER_TICK);
    return new BusRuntimeConfig(
        numbers.integer("bus.activeIntervalTicks", 5, 1, MAX_INTERVAL_TICKS),
        numbers.integer("bus.idleIntervalTicks", 40, 1, MAX_INTERVAL_TICKS),
        numbers.integer("bus.itemsPerOperation", 1, 1, MAX_ITEMS_PER_OPERATION),
        maxOperationsPerTick,
        numbers.integer("performance.bus.maxOperationsPerChunk", 40, 0, maxOperationsPerTick),
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
}
