package com.zxcmc.exort.integration.protection;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record ProtectionRuntimeConfig(
    boolean enabled, boolean failClosedOnError, Adapters adapters) {
  public ProtectionRuntimeConfig {
    Objects.requireNonNull(adapters, "adapters");
  }

  public static ProtectionRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new ProtectionRuntimeConfig(
        config.getBoolean("protection.enabled", true),
        config.getBoolean("protection.failClosedOnError", true),
        new Adapters(
            config.getBoolean("protection.adapters.worldguard", true),
            config.getBoolean("protection.adapters.griefPrevention", true),
            config.getBoolean("protection.adapters.towny", true),
            config.getBoolean("protection.adapters.lands", true),
            config.getBoolean("protection.adapters.residence", true)));
  }

  public record Adapters(
      boolean worldGuard,
      boolean griefPrevention,
      boolean towny,
      boolean lands,
      boolean residence) {}
}
