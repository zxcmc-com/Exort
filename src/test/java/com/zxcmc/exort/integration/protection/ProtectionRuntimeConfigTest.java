package com.zxcmc.exort.integration.protection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ProtectionRuntimeConfigTest {
  @Test
  void readsConfiguredPublicFlags() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("protection.enabled", false);
    yaml.set("protection.failClosedOnError", true);
    yaml.set("protection.adapters.worldguard", false);
    yaml.set("protection.adapters.griefPrevention", false);
    yaml.set("protection.adapters.towny", false);
    yaml.set("protection.adapters.lands", false);
    yaml.set("protection.adapters.residence", false);

    ProtectionRuntimeConfig config = ProtectionRuntimeConfig.fromConfig(yaml);

    assertFalse(config.enabled());
    assertTrue(config.failClosedOnError());
    assertFalse(config.adapters().worldGuard());
    assertFalse(config.adapters().griefPrevention());
    assertFalse(config.adapters().towny());
    assertFalse(config.adapters().lands());
    assertFalse(config.adapters().residence());
  }
}
