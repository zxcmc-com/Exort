package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RuntimeNetworkConfigTest {
  @Test
  void readsPublicWireAndRelayKeys() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wire.limit", 20);
    yaml.set("wire.hardCap", 50);
    yaml.set("relay.rangeChunks", 8);

    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(yaml);

    assertEquals(20, config.wireLimit());
    assertEquals(50, config.wireHardCap());
    assertEquals(8, config.relayRangeChunks());
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
  void relayRangeIsClamped() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("relay.rangeChunks", -2);

    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(yaml);

    assertEquals(0, config.relayRangeChunks());
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
