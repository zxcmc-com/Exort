package com.zxcmc.exort.storage;

import com.zxcmc.exort.gui.SortMode;
import com.zxcmc.exort.infra.config.ConfigEnums;
import com.zxcmc.exort.infra.config.ConfigNumbers;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public record StorageRuntimeConfig(
    String defaultSortModeName,
    int flushIntervalSeconds,
    long cacheIdleUnloadSeconds,
    long cacheIdleCheckSeconds) {
  static final int MAX_SCHEDULER_INTERVAL_SECONDS = 86_400;

  public StorageRuntimeConfig {
    Objects.requireNonNull(defaultSortModeName, "defaultSortModeName");
  }

  public long flushIntervalTicks() {
    return secondsToTicks(flushIntervalSeconds);
  }

  public long cacheIdleUnloadMillis() {
    return saturatingMultiply(cacheIdleUnloadSeconds, 1_000L);
  }

  public long cacheIdleCheckTicks() {
    return secondsToTicks(cacheIdleCheckSeconds);
  }

  public static StorageRuntimeConfig fromConfig(ConfigurationSection config) {
    return fromConfig(config, (Logger) null);
  }

  public static StorageRuntimeConfig fromConfig(ConfigurationSection config, Logger logger) {
    Objects.requireNonNull(config, "config");
    return fromNumbers(config, new ConfigNumbers(config, logger));
  }

  public static StorageRuntimeConfig fromNumbers(
      ConfigurationSection config, ConfigNumbers numbers) {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(numbers, "numbers");
    return new StorageRuntimeConfig(
        ConfigEnums.parse("defaultSortMode", config.getString("defaultSortMode"), SortMode.AMOUNT)
            .name(),
        (int)
            numbers.longInteger(
                "performance.storage.flushIntervalSeconds", 10, 0, MAX_SCHEDULER_INTERVAL_SECONDS),
        numbers.longInteger(
            "performance.storage.idleUnloadSeconds", 300, 0, MAX_SCHEDULER_INTERVAL_SECONDS),
        numbers.longInteger(
            "performance.storage.idleCheckSeconds", 60, 0, MAX_SCHEDULER_INTERVAL_SECONDS));
  }

  public static long secondsToTicks(long seconds) {
    return saturatingMultiply(Math.max(0L, seconds), 20L);
  }

  private static long saturatingMultiply(long value, long multiplier) {
    try {
      return Math.multiplyExact(value, multiplier);
    } catch (ArithmeticException overflow) {
      return Long.MAX_VALUE;
    }
  }
}
