package com.zxcmc.exort.gui;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record GuiRuntimeConfig(
    long sessionDeviceCheckIntervalTicks,
    double sessionMaxDeviceDistanceBlocks,
    long craftingConfirmTimeoutMs) {
  public static GuiRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    long seconds = config.getLong("crafting.confirmTimeoutSeconds", 10);
    return new GuiRuntimeConfig(
        Math.max(1L, config.getLong("session.deviceCheckIntervalTicks", 5L)),
        Math.max(1.0D, config.getDouble("session.maxDeviceDistanceBlocks", 8.0D)),
        Math.max(0L, seconds * 1000L));
  }

  public double sessionMaxDeviceDistanceSquared() {
    return sessionMaxDeviceDistanceBlocks * sessionMaxDeviceDistanceBlocks;
  }
}
