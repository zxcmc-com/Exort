package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
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
    assertEquals(8.0, config.forceVisibleDistance());
    assertEquals(600, config.maxVisibilityChangesPerTick());
    assertTrue(config.blockProxy().enabled());
    assertEquals(64.0, config.blockProxy().baseRenderDistanceBlocks());
    assertEquals(2.0, config.blockProxy().enterBufferBlocks());
    assertEquals(6.0, config.blockProxy().restoreBufferBlocks());
    assertEquals(8.0, config.blockProxy().forceRealDistance());
    assertEquals(1200, config.blockProxy().maxBlockChangesPerTick());
    assertTrue(config.adaptiveViewRange().enabled());
    assertEquals(List.of(320, 640, 1200), config.adaptiveViewRange().entityThresholds());
    assertEquals(List.of(240, 520, 960), config.adaptiveViewRange().recoverThresholds());
    assertEquals(0.7, config.adaptiveViewRange().rangeMultiplier(DisplayRole.BLOCK, 3));
    assertEquals(0.12, config.adaptiveViewRange().rangeMultiplier(DisplayRole.WIRE, 3));
    assertTrue(config.clientCullingBypass().enabled());
    assertTrue(config.clientCullingBypass().translationProbe().enabled());
    assertTrue(config.clientCullingBypass().translationProbe().requireModdedBrand());
    assertEquals(
        Set.of("fabric", "quilt", "forge", "neoforge"),
        config.clientCullingBypass().translationProbe().brandTokens());
    assertEquals(
        List.of("text.entityculling.title", "key.entityculling.toggle"),
        config.clientCullingBypass().translationProbe().translationKeys());
  }

  @Test
  void readsOnlyPublicTogglesAndKeepsTuningHardcoded() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.displayCulling.enabled", false);
    yaml.set("performance.displayCulling.backend", "packet-events");
    yaml.set("performance.displayCulling.forceVisibleDistance", 2.0);
    yaml.set("performance.displayCulling.blockProxy.enabled", false);
    yaml.set("performance.displayCulling.adaptiveViewRange.enabled", false);
    yaml.set("performance.displayCulling.clientCullingBypass.enabled", false);
    yaml.set("performance.displayCulling.clientCullingBypass.translationProbe", false);

    DisplayCullingConfig config = DisplayCullingConfig.fromConfig(yaml);

    assertFalse(config.enabled());
    assertEquals(DisplayCullingConfig.Backend.PACKET_EVENTS, config.backend());
    assertEquals(8.0, config.forceVisibleDistance());
    assertTrue(config.blockProxy().enabled());
    assertTrue(config.adaptiveViewRange().enabled());
    assertFalse(config.clientCullingBypass().enabled());
    assertFalse(config.clientCullingBypass().translationProbe().enabled());
  }

  @Test
  void invalidBackendFallsBackToAutoAtRuntime() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.displayCulling.backend", "bad");

    DisplayCullingConfig config = DisplayCullingConfig.fromConfig(yaml);

    assertEquals(DisplayCullingConfig.Backend.AUTO, config.backend());
  }
}
