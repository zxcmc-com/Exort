package com.zxcmc.exort.wireless;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record WirelessRuntimeConfig(boolean enabled, int rangeBlocks) {
  public static WirelessRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new WirelessRuntimeConfig(
        config.getBoolean("wireless.enabled", true),
        Math.max(0, config.getInt("wireless.rangeBlocks", 48)));
  }
}
