package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuntimeMonitorScreenConfigTest {
  @Test
  void resourceModeUsesCurrentMonitorScreenDefaults() {
    RuntimeMonitorScreenConfig config = RuntimeMonitorScreenConfig.forMode(true);

    assertEquals(0.56, config.item().offsetY());
    assertEquals(0.93, config.item().offsetZ());
    assertEquals(1.026, config.horizontalBlock().offsetZ());
    assertEquals(0.815, config.fullBlock().offsetZ());
    assertEquals(0.26, config.text().offsetY());
    assertEquals(0.95, config.text().offsetZ());
    assertEquals(0.7, config.textEmpty().scale());
    assertEquals(0, config.textBackgroundAlpha());
  }

  @Test
  void vanillaModeUsesCurrentMonitorScreenDefaults() {
    RuntimeMonitorScreenConfig config = RuntimeMonitorScreenConfig.forMode(false);

    assertEquals(0.62, config.item().offsetY());
    assertEquals(0.99, config.item().offsetZ());
    assertEquals(1.032, config.horizontalBlock().offsetZ());
    assertEquals(0.875, config.fullBlock().offsetZ());
    assertEquals(0.2, config.text().offsetY());
    assertEquals(1.01, config.text().offsetZ());
    assertEquals(0.8, config.textEmpty().scale());
  }
}
