package com.zxcmc.exort.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BusRuntimeConfigTest {
  @Test
  void defaultsMatchCurrentRuntimeConfig() {
    BusRuntimeConfig config = BusRuntimeConfig.fromConfig(new YamlConfiguration());

    assertEquals(5, config.activeIntervalTicks());
    assertEquals(40, config.idleIntervalTicks());
    assertEquals(1, config.itemsPerOperation());
    assertEquals(500, config.maxOperationsPerTick());
    assertEquals(40, config.maxOperationsPerChunk());
    assertTrue(config.allowStorageTargets());
    assertEquals(BusMode.WHITELIST, config.defaultImportMode());
    assertEquals(BusMode.WHITELIST, config.defaultExportMode());
  }

  @Test
  void configuredValuesOverrideDefaults() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("bus.activeIntervalTicks", 7);
    yaml.set("bus.idleIntervalTicks", 50);
    yaml.set("bus.itemsPerOperation", 3);
    yaml.set("performance.bus.maxOperationsPerTick", 6000);
    yaml.set("performance.bus.maxOperationsPerChunk", 600);
    yaml.set("bus.allowStorageTargets", false);
    yaml.set("bus.defaultMode.import", "BLACKLIST");
    yaml.set("bus.defaultMode.export", "ALL");

    BusRuntimeConfig config = BusRuntimeConfig.fromConfig(yaml);

    assertEquals(7, config.activeIntervalTicks());
    assertEquals(50, config.idleIntervalTicks());
    assertEquals(3, config.itemsPerOperation());
    assertEquals(6000, config.maxOperationsPerTick());
    assertEquals(600, config.maxOperationsPerChunk());
    assertFalse(config.allowStorageTargets());
    assertEquals(BusMode.BLACKLIST, config.defaultImportMode());
    assertEquals(BusMode.ALL, config.defaultExportMode());
    assertEquals(BusMode.BLACKLIST, config.defaultMode(false));
    assertEquals(BusMode.ALL, config.defaultMode(true));
    assertEquals(BusMode.BLACKLIST, config.defaultMode(BusType.IMPORT));
    assertEquals(BusMode.ALL, config.defaultMode(BusType.EXPORT));
  }

  @Test
  void clampsRuntimeLimitsAndFallsBackForInvalidModes() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("bus.activeIntervalTicks", 0);
    yaml.set("bus.idleIntervalTicks", -5);
    yaml.set("bus.itemsPerOperation", 0);
    yaml.set("performance.bus.maxOperationsPerTick", -1);
    yaml.set("performance.bus.maxOperationsPerChunk", -2);
    yaml.set("bus.defaultMode.import", "bad");
    yaml.set("bus.defaultMode.export", "");

    BusRuntimeConfig config = BusRuntimeConfig.fromConfig(yaml);

    assertEquals(1, config.activeIntervalTicks());
    assertEquals(1, config.idleIntervalTicks());
    assertEquals(1, config.itemsPerOperation());
    assertEquals(1, config.maxOperationsPerTick());
    assertEquals(0, config.maxOperationsPerChunk());
    assertEquals(BusMode.WHITELIST, config.defaultImportMode());
    assertEquals(BusMode.WHITELIST, config.defaultExportMode());
  }
}
