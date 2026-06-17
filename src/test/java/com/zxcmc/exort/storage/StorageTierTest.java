package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
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

  @Test
  void loadFromConfigHumanizesMissingDisplayName() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic_storage.maxItems", 128);
    config.set("basic_storage.material", "CHEST");

    StorageTier.loadFromConfig(config, LOGGER);

    var tier = StorageTier.fromString("basic_storage").orElseThrow();
    assertEquals("Basic Storage", tier.displayName());
    assertEquals("Basic Storage", tier.descriptor().displayName());
  }

  @Test
  void loadFromConfigHumanizesBlankDisplayName() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("wire-tier.maxItems", 128);
    config.set("wire-tier.material", "CHEST");
    config.set("wire-tier.displayName", " ");

    StorageTier.loadFromConfig(config, LOGGER);

    assertEquals("Wire Tier", StorageTier.fromString("wire-tier").orElseThrow().displayName());
  }

  @Test
  void descriptorUsesPublicImmutableProjection() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic.maxItems", 128);
    config.set("basic.material", "minecraft:chest");
    config.set("basic.displayName", "Basic");

    StorageTier.loadFromConfig(config, LOGGER);

    var descriptor = StorageTier.fromString("basic").orElseThrow().descriptor();
    assertEquals("BASIC", descriptor.key());
    assertEquals(128, descriptor.maxItems());
    assertEquals("minecraft:chest", descriptor.displayMaterialKey());
    assertEquals("Basic", descriptor.displayName());
  }

  @Test
  void allTiersReturnsImmutableSnapshot() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic.maxItems", 128);
    config.set("basic.material", "CHEST");
    StorageTier.loadFromConfig(config, LOGGER);

    Collection<StorageTier> snapshot = StorageTier.allTiers();
    assertEquals(1, snapshot.size());
    assertThrows(UnsupportedOperationException.class, snapshot::clear);

    YamlConfiguration replacement = new YamlConfiguration();
    replacement.set("gold.maxItems", 256);
    replacement.set("gold.material", "GOLD_BLOCK");
    replacement.set("diamond.maxItems", 512);
    replacement.set("diamond.material", "DIAMOND_BLOCK");
    StorageTier.loadFromConfig(replacement, LOGGER);

    assertEquals(1, snapshot.size());
    assertEquals(2, StorageTier.allTiers().size());
  }

  @Test
  void resolverUsesExistingTierAndRefreshesCapacitySnapshot() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", 256);
    config.set("gold.material", "GOLD_BLOCK");
    StorageTier.loadFromConfig(config, LOGGER);

    var resolution = StorageTierResolver.resolve("gold", 128L).orElseThrow();

    assertEquals("GOLD", resolution.tier().key());
    assertEquals(256L, resolution.tierMaxItems());
  }

  @Test
  void resolverFallsBackToSameCapacityTier() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", 256);
    config.set("gold.material", "GOLD_BLOCK");
    config.set("diamond.maxItems", 512);
    config.set("diamond.material", "DIAMOND_BLOCK");
    StorageTier.loadFromConfig(config, LOGGER);

    var resolution = StorageTierResolver.resolve("missing", 512L).orElseThrow();

    assertEquals("DIAMOND", resolution.tier().key());
    assertEquals(512L, resolution.tierMaxItems());
    assertTrue(resolution.fallback());
  }

  @Test
  void resolverFallsBackToClosestLowerCapacityTier() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", 256);
    config.set("gold.material", "GOLD_BLOCK");
    config.set("diamond.maxItems", 512);
    config.set("diamond.material", "DIAMOND_BLOCK");
    config.set("netherite.maxItems", 1024);
    config.set("netherite.material", "NETHERITE_BLOCK");
    StorageTier.loadFromConfig(config, LOGGER);

    var resolution = StorageTierResolver.resolve("missing", 900L).orElseThrow();

    assertEquals("DIAMOND", resolution.tier().key());
    assertEquals(512L, resolution.tierMaxItems());
  }

  @Test
  void resolverFallsBackToSmallestTierWhenSnapshotIsBelowAllTiers() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", 256);
    config.set("gold.material", "GOLD_BLOCK");
    config.set("diamond.maxItems", 512);
    config.set("diamond.material", "DIAMOND_BLOCK");
    StorageTier.loadFromConfig(config, LOGGER);

    var resolution = StorageTierResolver.resolve("missing", 128L).orElseThrow();

    assertEquals("GOLD", resolution.tier().key());
    assertEquals(256L, resolution.tierMaxItems());
  }

  @Test
  void resolverFallsBackToSmallestTierWhenSnapshotIsMissing() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", 256);
    config.set("gold.material", "GOLD_BLOCK");
    config.set("diamond.maxItems", 512);
    config.set("diamond.material", "DIAMOND_BLOCK");
    StorageTier.loadFromConfig(config, LOGGER);

    var resolution = StorageTierResolver.resolve("missing", null).orElseThrow();

    assertEquals("GOLD", resolution.tier().key());
    assertTrue(resolution.missingSnapshot());
  }

  @Test
  void resolverReturnsEmptyWhenNoTiersAreConfigured() {
    StorageTier.loadFromConfig(new YamlConfiguration(), LOGGER);

    assertTrue(StorageTierResolver.resolve("missing", 128L).isEmpty());
  }
}
