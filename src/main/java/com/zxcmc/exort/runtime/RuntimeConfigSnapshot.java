package com.zxcmc.exort.runtime;

import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.configuration.file.FileConfiguration;

/** Candidate configuration captured before a runtime generation is activated. */
public record RuntimeConfigSnapshot(
    FileConfiguration config,
    boolean resourceMode,
    boolean resourceWireUsesBarrier,
    Supplier<GuiRuntimeConfig> guiRuntimeConfig,
    Supplier<GuiOverlayConfig> guiOverlayConfig) {
  public RuntimeConfigSnapshot {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(guiRuntimeConfig, "guiRuntimeConfig");
    Objects.requireNonNull(guiOverlayConfig, "guiOverlayConfig");
  }
}
