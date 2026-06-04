package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WorldEditBulkConfigTest {
  @Test
  void readsDefaults() {
    WorldEditBulkConfig config = WorldEditBulkConfig.fromConfig(new YamlConfiguration());

    assertTrue(config.enabled());
    assertEquals(512, config.bulkThresholdBlocks());
    assertEquals(1500, config.markerUpdatesPerTick());
    assertEquals(2, config.refreshChunksPerTick());
    assertEquals(2, config.busScanChunksPerTick());
    assertEquals(32, config.networkStartsPerTick());
  }

  @Test
  void readsScalarEnabled() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.worldEditBulk", false);

    WorldEditBulkConfig config = WorldEditBulkConfig.fromConfig(yaml);

    assertFalse(config.enabled());
  }

  @Test
  void keepsBudgetsHardcoded() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.worldEditBulk.markerUpdatesPerTick", 1);

    WorldEditBulkConfig config = WorldEditBulkConfig.fromConfig(yaml);

    assertTrue(config.enabled());
    assertEquals(1500, config.markerUpdatesPerTick());
  }
}
