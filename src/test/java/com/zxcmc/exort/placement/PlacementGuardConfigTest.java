package com.zxcmc.exort.placement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PlacementGuardConfigTest {
  @Test
  void readsScalarEnabled() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("placementGuard", false);

    PlacementGuardConfig config = PlacementGuardConfig.fromConfig(yaml);

    assertFalse(config.enabled());
  }

  @Test
  void packetGuardOnlyUsesBasePacketEventsFlag() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("packetEvents.enabled", false);
    yaml.set("packetEvents.placementGuard.enabled", true);

    assertFalse(PlacementGuardConfig.fromConfig(yaml).packetEventsGuardEnabled());

    yaml.set("packetEvents.enabled", true);
    yaml.set("packetEvents.placementGuard.enabled", false);

    assertTrue(PlacementGuardConfig.fromConfig(yaml).packetEventsGuardEnabled());
  }
}
