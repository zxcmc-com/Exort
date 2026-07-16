package com.zxcmc.exort.runtime;

import com.zxcmc.exort.infra.config.ConfigNumbers;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public record RuntimeNetworkConfig(
    long storagePeekTicks,
    long wirePeekTicks,
    int wireLimit,
    int wireHardCap,
    boolean relayEnabled,
    int relayRangeChunks,
    boolean wireHardCapAdjusted) {
  private static final long TICKS_PER_SECOND = 20L;
  static final int MAX_WIRE_LIMIT = 32_768;
  static final int MAX_WIRE_HARD_CAP = 65_536;
  static final int MAX_RELAY_RANGE_CHUNKS = 1_024;

  public static RuntimeNetworkConfig fromConfig(ConfigurationSection config) {
    return fromConfig(config, (Logger) null);
  }

  public static RuntimeNetworkConfig fromConfig(ConfigurationSection config, Logger logger) {
    Objects.requireNonNull(config, "config");
    return fromNumbers(config, new ConfigNumbers(config, logger));
  }

  public static RuntimeNetworkConfig fromNumbers(
      ConfigurationSection config, ConfigNumbers numbers) {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(numbers, "numbers");

    long storagePeekTicks = 6 * TICKS_PER_SECOND;
    long wirePeekTicks = 6 * TICKS_PER_SECOND;

    int wireLimit = numbers.integer("wire.limit", 32, 1, MAX_WIRE_LIMIT);
    int hardCapFallback = Math.min(MAX_WIRE_HARD_CAP, wireLimit * 2);
    int configuredHardCap = config.getInt("wire.hardCap", hardCapFallback);
    int hardCapRaw = numbers.integer("wire.hardCap", hardCapFallback, 1, MAX_WIRE_HARD_CAP);
    boolean hardCapAdjusted = configuredHardCap < wireLimit;
    int wireHardCap = Math.max(wireLimit, hardCapRaw);
    boolean relayEnabled = config.getBoolean("relay.enabled", true);
    int relayRangeChunks = numbers.integer("relay.rangeChunks", 3, 0, MAX_RELAY_RANGE_CHUNKS);

    return new RuntimeNetworkConfig(
        storagePeekTicks,
        wirePeekTicks,
        wireLimit,
        wireHardCap,
        relayEnabled,
        relayRangeChunks,
        hardCapAdjusted);
  }
}
