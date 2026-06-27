package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StorageRuntimeConfigTest {
  @Test
  void readsConfiguredValues() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("defaultSortMode", "NAME");
    yaml.set("performance.storage.flushIntervalSeconds", 25);
    yaml.set("performance.storage.idleUnloadSeconds", 120L);
    yaml.set("performance.storage.idleCheckSeconds", 15L);

    StorageRuntimeConfig config = StorageRuntimeConfig.fromConfig(yaml);

    assertEquals("NAME", config.defaultSortModeName());
    assertEquals(25, config.flushIntervalSeconds());
    assertEquals(120L, config.cacheIdleUnloadSeconds());
    assertEquals(15L, config.cacheIdleCheckSeconds());
  }

  @Test
  void normalizesUnknownDefaultSortMode() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("defaultSortMode", "unknown");

    StorageRuntimeConfig config = StorageRuntimeConfig.fromConfig(yaml);

    assertEquals("AMOUNT", config.defaultSortModeName());
  }
}
