package com.zxcmc.exort.display;

import org.bukkit.configuration.ConfigurationSection;

public record WireAutoRenderConfig(
    int chunkRadius,
    int enterCompactWires,
    int exitCompactWires,
    double idlePlayerRadiusBlocks,
    int maintenanceBlocksPerTick) {
  public WireAutoRenderConfig {
    chunkRadius = Math.max(0, chunkRadius);
    enterCompactWires = Math.max(1, enterCompactWires);
    exitCompactWires = Math.max(0, Math.min(exitCompactWires, enterCompactWires));
    idlePlayerRadiusBlocks = Math.max(0.0, idlePlayerRadiusBlocks);
    maintenanceBlocksPerTick = Math.max(1, maintenanceBlocksPerTick);
  }

  public static WireAutoRenderConfig fromConfig(ConfigurationSection config) {
    String path = "resourceMode.wire.autoRender.";
    return new WireAutoRenderConfig(
        config.getInt(path + "chunkRadius", 1),
        config.getInt(path + "enterCompactWires", 48),
        config.getInt(path + "exitCompactWires", 32),
        config.getDouble(path + "idlePlayerRadiusBlocks", 96.0),
        config.getInt(path + "maintenanceBlocksPerTick", 16));
  }
}
