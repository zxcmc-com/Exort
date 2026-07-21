package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.logging.Logger;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageTierTest {
  private static final Logger LOGGER = Logger.getLogger(StorageTierTest.class.getName());

  @BeforeEach
  void resetCatalog() {
    StorageTier.resetForTests();
  }

  @Test
  void parsedCandidateCatalogIsInvisibleUntilAtomicPublication() {
    YamlConfiguration active = validCatalog("basic", 100L);
    assertTrue(StorageTier.loadFromConfig(active, LOGGER));
    YamlConfiguration candidate = validCatalog("rare", 200L);

    StorageTierCatalog parsed = StorageTierCatalog.parse(candidate, LOGGER);

    assertTrue(StorageTier.fromString("basic").isPresent());
    assertTrue(StorageTier.fromString("rare").isEmpty());
    StorageTierCatalog.publish(parsed);
    assertTrue(StorageTier.fromString("basic").isEmpty());
    assertEquals(200L, StorageTier.fromString("rare").orElseThrow().maxItems());
  }

  @Test
  void invalidReplacementKeepsLastValidCatalog() {
    YamlConfiguration valid = new YamlConfiguration();
    valid.set("basic.maxItems", 128);
    valid.set("basic.material", "CHEST");
    assertTrue(StorageTier.loadFromConfig(valid, LOGGER));

    assertFalse(StorageTier.loadFromConfig(new YamlConfiguration(), LOGGER));

    StorageTier tier = StorageTier.fromString("basic").orElseThrow();
    assertEquals(128, tier.maxItems());
    assertEquals(Material.CHEST, tier.displayMaterial());
  }

  @Test
  void normalizedDuplicateKeyKeepsLastValidCatalog() {
    YamlConfiguration valid = new YamlConfiguration();
    valid.set("basic.maxItems", 128);
    valid.set("basic.material", "CHEST");
    assertTrue(StorageTier.loadFromConfig(valid, LOGGER));
    YamlConfiguration duplicate = new YamlConfiguration();
    duplicate.set("rare.maxItems", 256);
    duplicate.set("rare.material", "GOLD_BLOCK");
    duplicate.set("RARE.maxItems", 512);
    duplicate.set("RARE.material", "DIAMOND_BLOCK");

    assertFalse(StorageTier.loadFromConfig(duplicate, LOGGER));

    assertTrue(StorageTier.fromString("basic").isPresent());
    assertTrue(StorageTier.fromString("rare").isEmpty());
  }

  @Test
  void loadFromConfigParsesPageSuffix() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic.maxItems", "2p");
    config.set("basic.material", "CHEST");
    config.set("basic.name", "Basic");

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
    assertEquals("Basic Storage", tier.fallbackDisplayName());
    assertEquals("Basic Storage", tier.descriptor().displayName());
  }

  @Test
  void loadFromConfigIgnoresLegacyDisplayName() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("rare.maxItems", 128);
    config.set("rare.material", "CHEST");
    config.set("rare.displayName", "Legacy Rare");

    StorageTier.loadFromConfig(config, LOGGER);

    var tier = StorageTier.fromString("rare").orElseThrow();
    assertEquals("Rare", tier.fallbackDisplayName());
    assertTrue(tier.translationKey().isEmpty());
  }

  @Test
  void loadFromConfigReadsTranslationPlaceholderName() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("rare.maxItems", 128);
    config.set("rare.material", "CHEST");
    config.set("rare.name", "{tier.rare}");

    StorageTier.loadFromConfig(config, LOGGER);

    var tier = StorageTier.fromString("rare").orElseThrow();
    assertEquals("Rare", tier.fallbackDisplayName());
    assertEquals("tier.rare", tier.translationKey().orElseThrow());
  }

  @Test
  void loadFromConfigParsesTierColorValues() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("hex.maxItems", 128);
    config.set("hex.material", "CHEST");
    config.set("hex.color", "#4b69ff");
    config.set("named.maxItems", 128);
    config.set("named.material", "CHEST");
    config.set("named.color", "<red>");

    StorageTier.loadFromConfig(config, LOGGER);

    assertEquals(
        TextColor.fromHexString("#4b69ff"), StorageTier.fromString("hex").orElseThrow().color());
    assertEquals(NamedTextColor.RED, StorageTier.fromString("named").orElseThrow().color());
  }

  @Test
  void loadFromConfigIgnoresInvalidTierColor() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("bad.maxItems", 128);
    config.set("bad.material", "CHEST");
    config.set("bad.color", "<#zzzzzz>");

    StorageTier.loadFromConfig(config, LOGGER);

    assertNull(StorageTier.fromString("bad").orElseThrow().color());
  }

  @Test
  void loadFromConfigHumanizesBlankName() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("wire-tier.maxItems", 128);
    config.set("wire-tier.material", "CHEST");
    config.set("wire-tier.name", " ");

    StorageTier.loadFromConfig(config, LOGGER);

    assertEquals(
        "Wire Tier", StorageTier.fromString("wire-tier").orElseThrow().fallbackDisplayName());
  }

  @Test
  void descriptorUsesPublicImmutableProjection() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic.maxItems", 128);
    config.set("basic.material", "minecraft:chest");
    config.set("basic.name", "Basic");

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
  void resolverPreservesMissingTierAndCapacityAsReadOnlyOrphan() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", 256);
    config.set("gold.material", "GOLD_BLOCK");
    config.set("diamond.maxItems", 512);
    config.set("diamond.material", "DIAMOND_BLOCK");
    StorageTier.loadFromConfig(config, LOGGER);

    var resolution = StorageTierResolver.resolve("missing", 512L).orElseThrow();

    assertEquals("MISSING", resolution.tier().key());
    assertEquals(512L, resolution.tierMaxItems());
    assertTrue(resolution.orphaned());
    assertTrue(resolution.tier().isReadOnly());
  }

  @Test
  void resolverNeverDowngradesOrphanCapacity() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", 256);
    config.set("gold.material", "GOLD_BLOCK");
    config.set("diamond.maxItems", 512);
    config.set("diamond.material", "DIAMOND_BLOCK");
    config.set("netherite.maxItems", 1024);
    config.set("netherite.material", "NETHERITE_BLOCK");
    StorageTier.loadFromConfig(config, LOGGER);

    var resolution = StorageTierResolver.resolve("missing", 900L).orElseThrow();

    assertEquals("MISSING", resolution.tier().key());
    assertEquals(900L, resolution.tierMaxItems());
    assertEquals(900L, resolution.tier().maxItems());
  }

  @Test
  void resolverPreservesSnapshotBelowConfiguredTiers() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", 256);
    config.set("gold.material", "GOLD_BLOCK");
    config.set("diamond.maxItems", 512);
    config.set("diamond.material", "DIAMOND_BLOCK");
    StorageTier.loadFromConfig(config, LOGGER);

    var resolution = StorageTierResolver.resolve("missing", 128L).orElseThrow();

    assertEquals("MISSING", resolution.tier().key());
    assertEquals(128L, resolution.tierMaxItems());
  }

  @Test
  void resolverFailsClosedWhenOrphanSnapshotIsMissing() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", 256);
    config.set("gold.material", "GOLD_BLOCK");
    config.set("diamond.maxItems", 512);
    config.set("diamond.material", "DIAMOND_BLOCK");
    StorageTier.loadFromConfig(config, LOGGER);

    assertTrue(StorageTierResolver.resolve("missing", null).isEmpty());
  }

  @Test
  void resolverPreservesOrphanEvenWhenNoTiersAreConfigured() {
    StorageTier.loadFromConfig(new YamlConfiguration(), LOGGER);

    var resolution = StorageTierResolver.resolve("missing", 128L).orElseThrow();
    assertEquals(128L, resolution.tierMaxItems());
    assertTrue(resolution.orphaned());
  }

  private static YamlConfiguration validCatalog(String key, long maxItems) {
    YamlConfiguration config = new YamlConfiguration();
    config.set(key + ".maxItems", maxItems);
    config.set(key + ".material", "CHEST");
    return config;
  }
}
