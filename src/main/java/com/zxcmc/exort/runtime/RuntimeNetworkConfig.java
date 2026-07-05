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

  public static RuntimeNetworkConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");

    long storagePeekTicks = 6 * TICKS_PER_SECOND;
    long wirePeekTicks = 6 * TICKS_PER_SECOND;

    int wireLimitRaw = config.getInt("wire.limit", 32);
    int wireLimit = Math.max(1, wireLimitRaw);
    int hardCapRaw = config.getInt("wire.hardCap", Math.max(wireLimit * 2, wireLimit));
    boolean hardCapAdjusted = hardCapRaw < wireLimit;
    int wireHardCap = Math.max(wireLimit, hardCapRaw);
    boolean relayEnabled = config.getBoolean("relay.enabled", true);
    int relayRangeChunks = Math.max(0, config.getInt("relay.rangeChunks", 3));

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
