package com.zxcmc.exort.display.culling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DisplayCullingConfigTest {
  @Test
  void readsPublicToggles() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.displayCulling.enabled", false);
    yaml.set("performance.displayCulling.backend", "packet-events");
    yaml.set("performance.displayCulling.clientCullingBypass.enabled", false);
    yaml.set("performance.displayCulling.clientCullingBypass.translationProbe", false);

    DisplayCullingConfig config = DisplayCullingConfig.fromConfig(yaml);

    assertFalse(config.enabled());
    assertEquals(DisplayCullingConfig.Backend.PACKET_EVENTS, config.backend());
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
