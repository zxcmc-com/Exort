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
    yaml.set("relay.enabled", false);
    yaml.set("relay.rangeChunks", 8);

    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(yaml);

    assertEquals(20, config.wireLimit());
    assertEquals(50, config.wireHardCap());
    assertFalse(config.relayEnabled());
    assertEquals(8, config.relayRangeChunks());
    assertFalse(config.wireHardCapAdjusted());
  }

  @Test
  void relayEnabledDefaultsTrue() {
    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(new YamlConfiguration());

    assertTrue(config.relayEnabled());
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

  @Test
  void capsTraversalBudgetsAtBoundedHotPathLimits() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wire.limit", Integer.MAX_VALUE);
    yaml.set("wire.hardCap", Integer.MAX_VALUE);

    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(yaml);

    assertEquals(RuntimeNetworkConfig.MAX_WIRE_LIMIT, config.wireLimit());
    assertEquals(RuntimeNetworkConfig.MAX_WIRE_HARD_CAP, config.wireHardCap());
  }

  @Test
  void capsRelayRangeAtBoundedHotPathLimit() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("relay.rangeChunks", Integer.MAX_VALUE);

    RuntimeNetworkConfig config = RuntimeNetworkConfig.fromConfig(yaml);

    assertEquals(RuntimeNetworkConfig.MAX_RELAY_RANGE_CHUNKS, config.relayRangeChunks());
  }
}
