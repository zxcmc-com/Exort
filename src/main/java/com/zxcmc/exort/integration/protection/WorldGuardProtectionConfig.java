package com.zxcmc.exort.integration.protection;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record WorldGuardProtectionConfig(boolean enabled, boolean failClosedOnError) {
  public static WorldGuardProtectionConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new WorldGuardProtectionConfig(
        config.getBoolean("worldguard.enabled", true),
        config.getBoolean("worldguard.failClosedOnError", false));
  }
}
