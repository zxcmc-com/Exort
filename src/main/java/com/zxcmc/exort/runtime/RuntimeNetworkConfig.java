package com.zxcmc.exort.runtime;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record RuntimeNetworkConfig(
    long storagePeekTicks,
    long wirePeekTicks,
    int wireLimit,
    int wireHardCap,
    boolean wireHardCapAdjusted) {
  private static final long TICKS_PER_SECOND = 20L;

  public static RuntimeNetworkConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");

    long storagePeekTicks =
        Math.max(1, config.getLong("bossbar.storagePeekSeconds", 6) * TICKS_PER_SECOND);
    long wirePeekTicks =
        Math.max(1, config.getLong("bossbar.wirePeekSeconds", 6) * TICKS_PER_SECOND);

    int wireLimitRaw = config.getInt("wire.limit", config.getInt("wireLimit", 32));
    int wireLimit = Math.max(1, wireLimitRaw);
    int hardCapRaw =
        config.getInt(
            "wire.hardCap", config.getInt("wireHardCap", Math.max(wireLimit * 2, wireLimit)));
    boolean hardCapAdjusted = hardCapRaw < wireLimit;
    int wireHardCap = Math.max(wireLimit, hardCapRaw);

    return new RuntimeNetworkConfig(
        storagePeekTicks, wirePeekTicks, wireLimit, wireHardCap, hardCapAdjusted);
  }
}
