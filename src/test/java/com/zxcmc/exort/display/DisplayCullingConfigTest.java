package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DisplayCullingConfigTest {
  @Test
  void readsDefaults() {
    DisplayCullingConfig config = DisplayCullingConfig.fromConfig(new YamlConfiguration());

    assertTrue(config.enabled());
    assertEquals(DisplayCullingConfig.Backend.AUTO, config.backend());
    assertEquals(10, config.intervalTicks());
    assertEquals(64.0, config.maxDistance());
    assertEquals(6.0, config.forceVisibleDistance());
    assertEquals(600, config.maxVisibilityChangesPerTick());
    assertTrue(config.adaptiveViewRange().enabled());
    assertEquals(List.of(320, 640, 1200), config.adaptiveViewRange().entityThresholds());
    assertEquals(List.of(240, 520, 960), config.adaptiveViewRange().recoverThresholds());
    assertEquals(3, config.adaptiveViewRange().denseIntervalsToStepDown());
    assertEquals(320, config.adaptiveViewRange().thresholdForLevel(0));
    assertEquals(640, config.adaptiveViewRange().thresholdForLevel(1));
    assertEquals(1200, config.adaptiveViewRange().thresholdForLevel(2));
    assertEquals(0.7, config.adaptiveViewRange().rangeMultiplier(DisplayRole.BLOCK, 3));
    assertEquals(0.12, config.adaptiveViewRange().rangeMultiplier(DisplayRole.WIRE, 3));
    assertEquals(0.1, config.adaptiveViewRange().rangeMultiplier(DisplayRole.MONITOR_CONTENT, 3));
    assertEquals(0.1, config.adaptiveViewRange().rangeMultiplier(DisplayRole.HOLOGRAM, 3));
    assertTrue(config.clientCullingBypass().enabled());
    assertTrue(config.clientCullingBypass().players().isEmpty());
  }

  @Test
  void clampsUnsafeValues() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.displayCulling.intervalTicks", -5);
    yaml.set("performance.displayCulling.maxDistance", 0.0);
    yaml.set("performance.displayCulling.forceVisibleDistance", 999.0);
    yaml.set("performance.displayCulling.maxVisibilityChangesPerTick", 0);
    yaml.set("performance.displayCulling.adaptiveViewRange.entityThresholds", List.of(-10, 20));
    yaml.set("performance.displayCulling.adaptiveViewRange.recoverThresholds", List.of(999));
    yaml.set("performance.displayCulling.adaptiveViewRange.denseIntervalsToStepDown", 0);
    yaml.set("performance.displayCulling.adaptiveViewRange.stableIntervalsToStepUp", 0);
    yaml.set("performance.displayCulling.adaptiveViewRange.stepDownCooldownTicks", 0);
    yaml.set("performance.displayCulling.adaptiveViewRange.stepUpCooldownTicks", 0);
    yaml.set("performance.displayCulling.adaptiveViewRange.roleRanges.block", List.of(2.0, 0.5));
    yaml.set("performance.displayCulling.adaptiveViewRange.roleRanges.wire", List.of(-1.0));

    DisplayCullingConfig config = DisplayCullingConfig.fromConfig(yaml);

    assertEquals(1, config.intervalTicks());
    assertEquals(1.0, config.maxDistance());
    assertEquals(1.0, config.forceVisibleDistance());
    assertEquals(1, config.maxVisibilityChangesPerTick());
    assertEquals(List.of(20), config.adaptiveViewRange().entityThresholds());
    assertEquals(List.of(20), config.adaptiveViewRange().recoverThresholds());
    assertEquals(1, config.adaptiveViewRange().denseIntervalsToStepDown());
    assertEquals(1, config.adaptiveViewRange().stableIntervalsToStepUp());
    assertEquals(1, config.adaptiveViewRange().stepDownCooldownTicks());
    assertEquals(1, config.adaptiveViewRange().stepUpCooldownTicks());
    assertEquals(1.0, config.adaptiveViewRange().rangeMultiplier(DisplayRole.BLOCK, 0));
    assertEquals(0.5, config.adaptiveViewRange().rangeMultiplier(DisplayRole.BLOCK, 1));
    assertEquals(0.05, config.adaptiveViewRange().rangeMultiplier(DisplayRole.WIRE, 0));
  }

  @Test
  void parsesProtocolLibBackend() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.displayCulling.backend", "protocol-lib");

    DisplayCullingConfig config = DisplayCullingConfig.fromConfig(yaml);

    assertEquals(DisplayCullingConfig.Backend.PROTOCOL_LIB, config.backend());
  }

  @Test
  void parsesClientCullingBypassPlayers() {
    UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.displayCulling.clientCullingBypass.enabled", false);
    yaml.set(
        "performance.displayCulling.clientCullingBypass.players",
        List.of(playerId.toString(), "not-a-uuid"));

    DisplayCullingConfig config = DisplayCullingConfig.fromConfig(yaml);

    assertFalse(config.clientCullingBypass().enabled());
    assertEquals(Set.of(playerId), config.clientCullingBypass().players());
  }
}
