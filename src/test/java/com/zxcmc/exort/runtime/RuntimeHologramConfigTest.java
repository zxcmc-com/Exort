package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RuntimeHologramConfigTest {
  @Test
  void resourceModeDisablesTerminalHologramButKeepsStorageDefaultEnabled() {
    RuntimeHologramConfig config = RuntimeHologramConfig.fromConfig(new YamlConfiguration(), true);

    assertFalse(config.terminal().enabled());
    assertEquals(0.95, config.terminal().offsetY());
    assertTrue(config.storage().enabled());
    assertEquals(0.5, config.storage().offsetZ());
    assertEquals(0.35, config.storage().scale());
  }

  @Test
  void vanillaModeUsesTerminalAndStorageDefaults() {
    RuntimeHologramConfig config = RuntimeHologramConfig.fromConfig(new YamlConfiguration(), false);

    assertTrue(config.terminal().enabled());
    assertEquals(0.83, config.terminal().offsetZ());
    assertTrue(config.storage().enabled());
    assertEquals(0.5, config.storage().offsetX());
  }

  @Test
  void configuredValuesOverrideModeSpecificDefaults() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("vanillaMode.terminalHologram.enabled", false);
    yaml.set("vanillaMode.terminalHologram.offset.z", 0.7);
    yaml.set("vanillaMode.storageHologram.offset.y", 0.8);
    yaml.set("vanillaMode.storageHologram.scale", 0.45);

    RuntimeHologramConfig config = RuntimeHologramConfig.fromConfig(yaml, false);

    assertFalse(config.terminal().enabled());
    assertEquals(0.7, config.terminal().offsetZ());
    assertEquals(0.8, config.storage().offsetY());
    assertEquals(0.45, config.storage().scale());
  }
}
