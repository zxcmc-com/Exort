package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StorageTierTest {
  private static final Logger LOGGER = Logger.getLogger(StorageTierTest.class.getName());

  @Test
  void loadFromConfigParsesPageSuffix() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic.maxItems", "2p");
    config.set("basic.material", "CHEST");
    config.set("basic.displayName", "Basic");

    StorageTier.loadFromConfig(config, LOGGER);

    var tier = StorageTier.fromString("basic");
    assertTrue(tier.isPresent());
    assertEquals(45L * 64L * 2L, tier.get().maxItems());
    assertEquals(Material.CHEST, tier.get().displayMaterial());
  }

  @Test
  void loadFromConfigFallsBackWhenPageSuffixOverflows() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("overflow.maxItems", Long.MAX_VALUE + "p");
    config.set("overflow.material", "CHEST");

    StorageTier.loadFromConfig(config, LOGGER);

    var tier = StorageTier.fromString("overflow");
    assertTrue(tier.isPresent());
    assertEquals(45L * 64L * 5L, tier.get().maxItems());
  }

  @Test
  void loadFromConfigFallsBackForNonPositiveCapacity() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("zero.maxItems", 0);
    config.set("zero.material", "CHEST");

    StorageTier.loadFromConfig(config, LOGGER);

    var tier = StorageTier.fromString("zero");
    assertTrue(tier.isPresent());
    assertEquals(45L * 64L * 5L, tier.get().maxItems());
  }

  @Test
  void loadFromConfigAcceptsNamespacedMaterial() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic.maxItems", 128);
    config.set("basic.material", "minecraft:chest");

    StorageTier.loadFromConfig(config, LOGGER);

    var tier = StorageTier.fromString("BASIC");
    assertTrue(tier.isPresent());
    assertEquals(Material.CHEST, tier.get().displayMaterial());
  }
}
