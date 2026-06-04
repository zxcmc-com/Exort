package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RuntimeNetworkConfigTest {
  @Test
  void defaultsMatchCurrentRuntimeConfig() {
    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(new YamlConfiguration());

    assertEquals(120L, config.storagePeekTicks());
    assertEquals(120L, config.wirePeekTicks());
    assertEquals(32, config.wireLimit());
    assertEquals(64, config.wireHardCap());
    assertFalse(config.wireHardCapAdjusted());
  }

  @Test
  void readsPublicWireKeys() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wire.limit", 20);
    yaml.set("wire.hardCap", 50);

    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(yaml);

    assertEquals(20, config.wireLimit());
    assertEquals(50, config.wireHardCap());
    assertFalse(config.wireHardCapAdjusted());
  }

  @Test
  void wireLimitAndHardCapAreClamped() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wire.limit", -3);
    yaml.set("wire.hardCap", 0);

    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(yaml);

    assertEquals(1, config.wireLimit());
    assertEquals(1, config.wireHardCap());
    assertTrue(config.wireHardCapAdjusted());
  }

  @Test
  void wireHardCapZeroStillUsesRuntimeMinimumForBenchmarkConsistency() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wire.limit", 12);
    yaml.set("wire.hardCap", 0);

    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(yaml);

    assertEquals(12, config.wireLimit());
    assertEquals(12, config.wireHardCap());
    assertTrue(config.wireHardCapAdjusted());
  }
}
