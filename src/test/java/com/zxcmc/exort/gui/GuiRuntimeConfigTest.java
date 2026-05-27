package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GuiRuntimeConfigTest {
  @Test
  void readsCurrentDefaults() {
    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(new YamlConfiguration());

    assertEquals(5L, config.sessionDeviceCheckIntervalTicks());
    assertEquals(8.0D, config.sessionMaxDeviceDistanceBlocks());
    assertEquals(64.0D, config.sessionMaxDeviceDistanceSquared());
    assertEquals(10_000L, config.craftingConfirmTimeoutMs());
  }

  @Test
  void readsConfiguredValues() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("session.deviceCheckIntervalTicks", 12L);
    yaml.set("session.maxDeviceDistanceBlocks", 14.5D);
    yaml.set("crafting.confirmTimeoutSeconds", 3L);

    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(yaml);

    assertEquals(12L, config.sessionDeviceCheckIntervalTicks());
    assertEquals(14.5D, config.sessionMaxDeviceDistanceBlocks());
    assertEquals(210.25D, config.sessionMaxDeviceDistanceSquared());
    assertEquals(3_000L, config.craftingConfirmTimeoutMs());
  }

  @Test
  void clampsInvalidValuesToCurrentRuntimeMinimums() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("session.deviceCheckIntervalTicks", 0L);
    yaml.set("session.maxDeviceDistanceBlocks", 0.0D);
    yaml.set("crafting.confirmTimeoutSeconds", -2L);

    GuiRuntimeConfig config = GuiRuntimeConfig.fromConfig(yaml);

    assertEquals(1L, config.sessionDeviceCheckIntervalTicks());
    assertEquals(1.0D, config.sessionMaxDeviceDistanceBlocks());
    assertEquals(1.0D, config.sessionMaxDeviceDistanceSquared());
    assertEquals(0L, config.craftingConfirmTimeoutMs());
  }
}
