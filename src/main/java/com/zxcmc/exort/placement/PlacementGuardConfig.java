package com.zxcmc.exort.placement;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record PlacementGuardConfig(
    boolean enabled,
    int pollIntervalTicks,
    int targetRangeBlocks,
    double guardScale,
    double cornerInset,
    boolean protocolLibGuardEnabled) {
  public static PlacementGuardConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new PlacementGuardConfig(
        config.getBoolean("placementGuard.enabled", true),
        config.getInt("placementGuard.pollIntervalTicks", 1),
        config.getInt("placementGuard.targetRangeBlocks", 5),
        config.getDouble("placementGuard.guardScale", 0.0625),
        config.getDouble("placementGuard.cornerInset", 0.01),
        config.getBoolean("protocolLib.enabled", true)
            && config.getBoolean("protocolLib.placementGuard.enabled", true));
  }
}
