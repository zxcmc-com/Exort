package com.zxcmc.exort.runtime;

import org.bukkit.Material;

public record RuntimeDisplayConfig(
    Material displayBaseMaterial,
    double displayScale,
    double offsetX,
    double offsetY,
    double offsetZ) {
  public static RuntimeDisplayConfig defaults() {
    return new RuntimeDisplayConfig(Material.PAPER, 1.0, 0.5, 0.5, 0.5);
  }
}
