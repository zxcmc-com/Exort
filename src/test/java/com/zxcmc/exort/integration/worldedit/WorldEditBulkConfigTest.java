package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void clampsBudgetsToAtLeastOne() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.worldEditBulk.bulkThresholdBlocks", 0);
    yaml.set("performance.worldEditBulk.markerUpdatesPerTick", -10);
    yaml.set("performance.worldEditBulk.refreshChunksPerTick", 0);
    yaml.set("performance.worldEditBulk.busScanChunksPerTick", -1);
    yaml.set("performance.worldEditBulk.networkStartsPerTick", 0);

    WorldEditBulkConfig config = WorldEditBulkConfig.fromConfig(yaml);

    assertEquals(1, config.bulkThresholdBlocks());
    assertEquals(1, config.markerUpdatesPerTick());
    assertEquals(1, config.refreshChunksPerTick());
    assertEquals(1, config.busScanChunksPerTick());
    assertEquals(1, config.networkStartsPerTick());
  }
}
