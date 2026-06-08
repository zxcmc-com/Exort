package com.zxcmc.exort.runtime;

import com.zxcmc.exort.carrier.Carriers;
import java.util.Locale;
import org.bukkit.Material;

public record RuntimeItemModelConfig(
    String displayNamespace,
    Material wireMaterial,
    Material storageCarrier,
    Material terminalCarrier,
    Material monitorCarrier,
    Material busCarrier,
    String wireItemModel,
    String storageItemModel,
    String terminalItemModel,
    String craftingTerminalItemModel,
    String monitorItemModel,
    String importBusItemModel,
    String exportBusItemModel,
    String wirelessItemModel,
    String wirelessDisabledModel) {
  private static final String VANILLA_NAMESPACE = "minecraft";

  public static RuntimeItemModelConfig forMode(boolean resourceMode) {
    return forMode(resourceMode, false);
  }

  public static RuntimeItemModelConfig forMode(
      boolean resourceMode, boolean resourceWireUsesBarrier) {
    return resourceMode ? resourceConfig(resourceWireUsesBarrier) : vanillaConfig();
  }

  public static String normalizeModelId(String raw, String namespace) {
    String ns = namespace == null || namespace.isBlank() ? VANILLA_NAMESPACE : namespace.trim();
    String id = raw == null ? "" : raw.trim();
    if (id.isEmpty()) id = "unknown";
    id = id.toLowerCase(Locale.ROOT);
    int colon = id.indexOf(':');
    if (colon >= 0 && colon + 1 < id.length()) {
      id = id.substring(colon + 1);
    }
    return ns + ":" + id;
  }

  private static RuntimeItemModelConfig resourceConfig(boolean resourceWireUsesBarrier) {
    String resourceNamespace = "exort";
    return new RuntimeItemModelConfig(
        resourceNamespace,
        resourceWireUsesBarrier ? Carriers.CARRIER_BARRIER : Carriers.CHORUS_MATERIAL,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        normalizeModelId("wire/center", resourceNamespace),
        normalizeModelId("storage/storage", resourceNamespace),
        normalizeModelId("terminal/inventory", resourceNamespace),
        normalizeModelId("terminal/workbench", resourceNamespace),
        normalizeModelId("terminal/monitor", resourceNamespace),
        normalizeModelId("bus/import", resourceNamespace),
        normalizeModelId("bus/export", resourceNamespace),
        normalizeModelId("terminal/wireless", resourceNamespace),
        normalizeModelId("terminal/wireless_disabled", resourceNamespace));
  }

  private static RuntimeItemModelConfig vanillaConfig() {
    return new RuntimeItemModelConfig(
        VANILLA_NAMESPACE,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        normalizeModelId("black_stained_glass", VANILLA_NAMESPACE),
        normalizeModelId("vault", VANILLA_NAMESPACE),
        normalizeModelId("barrel", VANILLA_NAMESPACE),
        normalizeModelId("crafting_table", VANILLA_NAMESPACE),
        normalizeModelId("smooth_stone", VANILLA_NAMESPACE),
        normalizeModelId("dispenser", VANILLA_NAMESPACE),
        normalizeModelId("dropper", VANILLA_NAMESPACE),
        normalizeModelId("target", VANILLA_NAMESPACE),
        normalizeModelId("target", VANILLA_NAMESPACE));
  }
}
