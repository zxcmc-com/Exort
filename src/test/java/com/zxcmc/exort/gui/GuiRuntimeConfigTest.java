package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GuiRuntimeConfigTest {
  @Test
  void readsMovedSessionCheckInterval() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.sessionDeviceCheckIntervalTicks", 12L);
    yaml.set("performance.gui.indexEntriesPerTick", 1024);
    yaml.set("performance.gui.indexBudgetMicros", 5000);

    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(yaml);

    assertEquals(12L, config.sessionDeviceCheckIntervalTicks());
    assertEquals(1024, config.indexEntriesPerTick());
    assertEquals(5000, config.indexBudgetMicros());
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

  @Test
  void clampsGuiIndexBudgets() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.gui.indexEntriesPerTick", 0L);
    yaml.set("performance.gui.indexBudgetMicros", Long.MAX_VALUE);

    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(yaml);

    assertEquals(32, config.indexEntriesPerTick());
    assertEquals(10_000, config.indexBudgetMicros());
  }
}
