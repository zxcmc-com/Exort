package com.zxcmc.exort.placement.bridge;

import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import java.util.Objects;
import java.util.function.Supplier;

/** Generation-scoped placement policy and sound/bus configuration. */
public record PlacementRuntimeContext(
    int wireHardCap,
    boolean relayEnabled,
    boolean wirelessEnabled,
    Supplier<BreakSoundConfig> breakSoundConfig,
    Supplier<BusRuntimeConfig> busRuntimeConfig) {
  public PlacementRuntimeContext {
    Objects.requireNonNull(breakSoundConfig, "breakSoundConfig");
    Objects.requireNonNull(busRuntimeConfig, "busRuntimeConfig");
  }
}
