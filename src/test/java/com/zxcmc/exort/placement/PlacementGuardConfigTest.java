package com.zxcmc.exort.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PlacementGuardConfigTest {
  @Test
  void defaultsMatchCurrentPlacementGuardConfig() {
    PlacementGuardConfig config = PlacementGuardConfig.fromConfig(new YamlConfiguration());

    assertTrue(config.enabled());
    assertEquals(1, config.pollIntervalTicks());
    assertEquals(5, config.targetRangeBlocks());
    assertEquals(0.0625, config.guardScale());
    assertEquals(0.01, config.cornerInset());
    assertTrue(config.protocolLibGuardEnabled());
  }

  @Test
  void configuredValuesOverrideDefaults() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("placementGuard.enabled", false);
    yaml.set("placementGuard.pollIntervalTicks", 3);
    yaml.set("placementGuard.targetRangeBlocks", 7);
    yaml.set("placementGuard.guardScale", 0.125);
    yaml.set("placementGuard.cornerInset", 0.03);

    PlacementGuardConfig config = PlacementGuardConfig.fromConfig(yaml);

    assertFalse(config.enabled());
    assertEquals(3, config.pollIntervalTicks());
    assertEquals(7, config.targetRangeBlocks());
    assertEquals(0.125, config.guardScale());
    assertEquals(0.03, config.cornerInset());
  }

  @Test
  void protocolLibGuardRequiresBothProtocolFlags() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("protocolLib.enabled", false);
    yaml.set("protocolLib.placementGuard.enabled", true);

    assertFalse(PlacementGuardConfig.fromConfig(yaml).protocolLibGuardEnabled());

    yaml.set("protocolLib.enabled", true);
    yaml.set("protocolLib.placementGuard.enabled", false);

    assertFalse(PlacementGuardConfig.fromConfig(yaml).protocolLibGuardEnabled());

    yaml.set("protocolLib.placementGuard.enabled", true);

    assertTrue(PlacementGuardConfig.fromConfig(yaml).protocolLibGuardEnabled());
  }
}
