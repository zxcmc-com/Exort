package com.zxcmc.exort.wireless;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record WirelessRuntimeConfig(boolean enabled, int rangeBlocks) {
  static final int MAX_RANGE_BLOCKS = 32_768;

  public static WirelessRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new WirelessRuntimeConfig(
        config.getBoolean("wireless.enabled", true),
        clamp(config.getInt("wireless.rangeBlocks", 48), 0, MAX_RANGE_BLOCKS));
  }

  private static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }
}
