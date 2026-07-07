package com.zxcmc.exort.infra.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class FeatureAccessConfigTest {
  @Test
  void defaultsKeepPlayerFacingFeaturesVisible() {
    FeatureAccessConfig config = FeatureAccessConfig.defaults();

    assertTrue(config.isCatalogVisible("relay"));
    assertTrue(config.isCatalogVisible("chunk_loader"));
    assertTrue(config.isCatalogVisible("personal_chunk_loader"));
    assertTrue(config.isCatalogVisible("dormant_chunk_loader"));
    assertTrue(config.isCatalogVisible("transmitter"));
    assertTrue(config.isCatalogVisible("wireless_terminal"));
  }

  @Test
  void readsRelayChunkLoaderAndWirelessFlags() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("relay.enabled", false);
    yaml.set("chunkLoader.enabled", false);
    yaml.set("wireless.enabled", false);

    FeatureAccessConfig config = FeatureAccessConfig.fromConfig(yaml);

    assertFalse(config.allowsRecipeResult("exort:relay"));
    assertFalse(config.allowsRecipeResult("chunk_loader"));
    assertFalse(config.allowsRecipeResult("personal_chunk_loader"));
    assertFalse(config.allowsRecipeResult("dormant_chunk_loader"));
    assertFalse(config.allowsRecipeResult("transmitter"));
    assertFalse(config.allowsRecipeResult("wireless_terminal"));
    assertTrue(config.allowsRecipeResult("wire"));
    assertTrue(config.allowsRecipeResult("storage:common"));
  }
}
