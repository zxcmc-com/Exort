package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GuiRuntimeConfigTest {
  @Test
  void readsMovedSessionCheckInterval() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.sessionDeviceCheckIntervalTicks", 12L);

    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(yaml);

    assertEquals(12L, config.sessionDeviceCheckIntervalTicks());
  }

  @Test
  void clampsInvalidSessionCheckInterval() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.sessionDeviceCheckIntervalTicks", 0L);

    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(yaml);

    assertEquals(1L, config.sessionDeviceCheckIntervalTicks());
  }

  @Test
  void capsExcessiveSessionCheckInterval() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.sessionDeviceCheckIntervalTicks", Long.MAX_VALUE);

    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(yaml);

    assertEquals(
        GuiRuntimeConfig.MAX_SESSION_DEVICE_CHECK_INTERVAL_TICKS,
        config.sessionDeviceCheckIntervalTicks());
  }
}
