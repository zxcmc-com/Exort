package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WorldEditBulkConfigTest {
  @Test
  void readsScalarEnabled() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("performance.worldEditBulk", false);

    WorldEditBulkConfig config = WorldEditBulkConfig.fromConfig(yaml);

    assertFalse(config.enabled());
  }
}
