package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RuntimeDisplayConfigTest {
  @Test
  void defaultsMatchCurrentResourceDisplaySettings() {
    RuntimeDisplayConfig config = RuntimeDisplayConfig.defaults();

    assertEquals(Material.PAPER, config.displayBaseMaterial());
    assertEquals(1.0, config.displayScale());
    assertEquals(0.5, config.offsetX());
    assertEquals(0.5, config.offsetY());
    assertEquals(0.5, config.offsetZ());
  }
}
