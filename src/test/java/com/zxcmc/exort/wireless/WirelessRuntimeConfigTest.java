package com.zxcmc.exort.wireless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WirelessRuntimeConfigTest {
  @Test
  void readsConfiguredValues() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wireless.enabled", false);
    yaml.set("wireless.rangeBlocks", 96);

    WirelessRuntimeConfig config = WirelessRuntimeConfig.fromConfig(yaml);

    assertFalse(config.enabled());
    assertEquals(96, config.rangeBlocks());
  }

  @Test
  void clampsNegativeRange() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wireless.rangeBlocks", -2);

    WirelessRuntimeConfig config = WirelessRuntimeConfig.fromConfig(yaml);

    assertEquals(0, config.rangeBlocks());
  }
}
