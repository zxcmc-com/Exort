package com.zxcmc.exort.runtime;

import java.util.Objects;
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
    Objects.requireNonNull(config, "config");

    long storagePeekTicks = 6 * TICKS_PER_SECOND;
    long wirePeekTicks = 6 * TICKS_PER_SECOND;

    int wireLimitRaw = config.getInt("wire.limit", 32);
    int wireLimit = clamp(wireLimitRaw, 1, MAX_WIRE_LIMIT);
    int hardCapRaw = config.getInt("wire.hardCap", Math.min(MAX_WIRE_HARD_CAP, wireLimit * 2));
    boolean hardCapAdjusted = hardCapRaw < wireLimit;
    int wireHardCap = Math.max(wireLimit, clamp(hardCapRaw, 1, MAX_WIRE_HARD_CAP));
    boolean relayEnabled = config.getBoolean("relay.enabled", true);
    int relayRangeChunks = clamp(config.getInt("relay.rangeChunks", 3), 0, MAX_RELAY_RANGE_CHUNKS);

    return new RuntimeNetworkConfig(
        storagePeekTicks,
        wirePeekTicks,
        wireLimit,
        wireHardCap,
        relayEnabled,
        relayRangeChunks,
        hardCapAdjusted);
  }

  private static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }
}
