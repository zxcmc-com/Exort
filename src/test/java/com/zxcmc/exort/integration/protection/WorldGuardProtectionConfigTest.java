package com.zxcmc.exort.integration.protection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WorldGuardProtectionConfigTest {
  @Test
  void readsCurrentDefaults() {
    WorldGuardProtectionConfig config =
        WorldGuardProtectionConfig.fromConfig(new YamlConfiguration());

    assertTrue(config.enabled());
    assertFalse(config.failClosedOnError());
    assertFalse(config.debug());
  }

  @Test
  void readsConfiguredFlags() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("worldguard.enabled", false);
    yaml.set("worldguard.failClosedOnError", true);
    yaml.set("worldguard.debug", true);

    WorldGuardProtectionConfig config = WorldGuardProtectionConfig.fromConfig(yaml);

    assertFalse(config.enabled());
    assertTrue(config.failClosedOnError());
    assertTrue(config.debug());
  }
}
