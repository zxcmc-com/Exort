package com.zxcmc.exort.gui;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record GuiRuntimeConfig(
    long sessionDeviceCheckIntervalTicks, long craftingConfirmTimeoutMs) {
  private static final long CRAFTING_CONFIRM_TIMEOUT_MS = 10_000L;

  public static GuiRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new GuiRuntimeConfig(
        Math.max(1L, config.getLong("performance.sessionDeviceCheckIntervalTicks", 5L)),
        CRAFTING_CONFIRM_TIMEOUT_MS);
  }
}
