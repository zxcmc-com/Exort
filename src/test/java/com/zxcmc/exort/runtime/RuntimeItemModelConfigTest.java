package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.carrier.Carriers;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RuntimeItemModelConfigTest {
  @Test
  void resourceDefaultsUseExortNamespaceAndResourceCarriers() {
    RuntimeItemModelConfig config = RuntimeItemModelConfig.forMode(true);

    assertEquals("exort", config.displayNamespace());
    assertEquals(Carriers.CHORUS_MATERIAL, config.wireMaterial());
    assertEquals(Carriers.CARRIER_BARRIER, config.storageCarrier());
    assertEquals(Carriers.CARRIER_BARRIER, config.relayCarrier());
    assertEquals("exort:wire/center", config.wireItemModel());
    assertEquals("exort:storage/storage", config.storageItemModel());
    assertEquals("exort:relay/relay", config.relayItemModel());
    assertEquals("exort:terminal/wireless_disabled", config.wirelessDisabledModel());
  }

  @Test
  void resourceBarrierWireCarrierKeepsResourceModels() {
    RuntimeItemModelConfig config = RuntimeItemModelConfig.forMode(true, true);

    assertEquals("exort", config.displayNamespace());
    assertEquals(Carriers.CARRIER_BARRIER, config.wireMaterial());
    assertEquals(Carriers.CARRIER_BARRIER, config.storageCarrier());
    assertEquals("exort:wire/center", config.wireItemModel());
    assertEquals("exort:storage/storage", config.storageItemModel());
  }

  @Test
  void vanillaDefaultsUseMinecraftNamespaceAndBarrierCarriers() {
    RuntimeItemModelConfig config = RuntimeItemModelConfig.forMode(false);

    assertEquals("minecraft", config.displayNamespace());
    assertEquals(Material.BARRIER, config.wireMaterial());
    assertEquals(Material.BARRIER, config.terminalCarrier());
    assertEquals("minecraft:black_stained_glass", config.wireItemModel());
    assertEquals("minecraft:barrel", config.terminalItemModel());
    assertEquals("minecraft:lodestone", config.relayItemModel());
    assertEquals("minecraft:target", config.wirelessDisabledModel());
  }

  @Test
  void blankModelFallsBackToUnknownInNamespace() {
    assertEquals("minecraft:unknown", RuntimeItemModelConfig.normalizeModelId("  ", null));
  }
}
