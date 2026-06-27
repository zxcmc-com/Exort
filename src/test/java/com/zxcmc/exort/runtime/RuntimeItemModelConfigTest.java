package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.carrier.Carriers;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class RuntimeItemModelConfigTest {
  @Test
  void resourceModeUsesExortModelsAndConfiguredWireCarrier() {
    RuntimeItemModelConfig chorusWire = RuntimeItemModelConfig.forMode(true, false);
    RuntimeItemModelConfig barrierWire = RuntimeItemModelConfig.forMode(true, true);

    assertEquals(Carriers.CHORUS_MATERIAL, chorusWire.wireMaterial());
    assertEquals(Carriers.CARRIER_BARRIER, barrierWire.wireMaterial());
    assertEquals(Carriers.CARRIER_BARRIER, barrierWire.storageCarrier());
    assertAllModelsUseNamespace(barrierWire, "exort:");
  }

  @Test
  void vanillaModeUsesBarrierCarriersAndMinecraftModels() {
    RuntimeItemModelConfig config = RuntimeItemModelConfig.forMode(false);

    assertEquals(Material.BARRIER, config.wireMaterial());
    assertEquals(Material.BARRIER, config.terminalCarrier());
    assertAllModelsUseNamespace(config, "minecraft:");
  }

  @Test
  void normalizeModelIdStripsInputNamespaceAndFallsBackForBlankValues() {
    assertEquals(
        "custom:path/model", RuntimeItemModelConfig.normalizeModelId("exort:path/model", "custom"));
    assertEquals("minecraft:unknown", RuntimeItemModelConfig.normalizeModelId("  ", null));
  }

  private static void assertAllModelsUseNamespace(RuntimeItemModelConfig config, String prefix) {
    for (String model : models(config)) {
      assertTrue(model.startsWith(prefix), model);
    }
  }

  private static List<String> models(RuntimeItemModelConfig config) {
    return List.of(
        config.wireItemModel(),
        config.storageItemModel(),
        config.terminalItemModel(),
        config.craftingTerminalItemModel(),
        config.monitorItemModel(),
        config.importBusItemModel(),
        config.exportBusItemModel(),
        config.relayItemModel(),
        config.wirelessItemModel(),
        config.wirelessDisabledModel());
  }
}
