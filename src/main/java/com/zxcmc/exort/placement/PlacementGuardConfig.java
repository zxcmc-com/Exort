package com.zxcmc.exort.placement;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record PlacementGuardConfig(
    boolean enabled,
    int pollIntervalTicks,
    int targetRangeBlocks,
    double guardScale,
    double cornerInset,
    boolean packetEventsGuardEnabled) {
  public static PlacementGuardConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new PlacementGuardConfig(
        config.getBoolean("placementGuard", true),
        1,
        5,
        0.0625,
        0.065,
        config.getBoolean("packetEvents.enabled", true));
  }
}
