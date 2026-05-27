package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.carrier.Carriers;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RuntimeItemModelConfigTest {
  @Test
  void resourceDefaultsUseExortNamespaceAndResourceCarriers() {
    RuntimeItemModelConfig config =
        RuntimeItemModelConfig.fromConfig(new YamlConfiguration(), true);

    assertEquals("exort", config.displayNamespace());
    assertEquals(Carriers.CHORUS_MATERIAL, config.wireMaterial());
    assertEquals(Carriers.CARRIER_BARRIER, config.storageCarrier());
    assertEquals("exort:wire/center", config.wireItemModel());
    assertEquals("exort:storage/storage", config.storageItemModel());
    assertEquals("exort:terminal/wireless_disabled", config.wirelessDisabledModel());
  }

  @Test
  void vanillaDefaultsUseMinecraftNamespaceAndBarrierCarriers() {
    RuntimeItemModelConfig config =
        RuntimeItemModelConfig.fromConfig(new YamlConfiguration(), false);

    assertEquals("minecraft", config.displayNamespace());
    assertEquals(Material.BARRIER, config.wireMaterial());
    assertEquals(Material.BARRIER, config.terminalCarrier());
    assertEquals("minecraft:black_stained_glass", config.wireItemModel());
    assertEquals("minecraft:barrel", config.terminalItemModel());
    assertEquals("minecraft:target", config.wirelessDisabledModel());
  }

  @Test
  void customResourceModelsAreNormalizedIntoConfiguredNamespace() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("resourceMode.namespace", "custom");
    yaml.set("resourceMode.wire.itemModel", "minecraft:Wire/Center");
    yaml.set("resourceMode.bus.export.itemModel", "Bus/EXPORT");

    RuntimeItemModelConfig config = RuntimeItemModelConfig.fromConfig(yaml, true);

    assertEquals("custom:wire/center", config.wireItemModel());
    assertEquals("custom:bus/export", config.exportBusItemModel());
  }

  @Test
  void blankModelFallsBackToUnknownInNamespace() {
    assertEquals("minecraft:unknown", RuntimeItemModelConfig.normalizeModelId("  ", null));
  }
}
