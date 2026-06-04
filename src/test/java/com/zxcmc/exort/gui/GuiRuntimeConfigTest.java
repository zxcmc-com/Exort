package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GuiRuntimeConfigTest {
  @Test
  void readsCurrentDefaults() {
    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(new YamlConfiguration());

    assertEquals(5L, config.sessionDeviceCheckIntervalTicks());
    assertEquals(10_000L, config.craftingConfirmTimeoutMs());
  }

  @Test
  void readsMovedSessionCheckInterval() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.sessionDeviceCheckIntervalTicks", 12L);

    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(yaml);

    assertEquals(12L, config.sessionDeviceCheckIntervalTicks());
    assertEquals(10_000L, config.craftingConfirmTimeoutMs());
  }

  @Test
  void clampsInvalidSessionCheckInterval() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.sessionDeviceCheckIntervalTicks", 0L);

    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(yaml);

    assertEquals(1L, config.sessionDeviceCheckIntervalTicks());
    assertEquals(10_000L, config.craftingConfirmTimeoutMs());
  }
}
