package com.zxcmc.exort.runtime;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.items.CustomItemModelConfig;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

public record RuntimeItemModelConfig(
    String displayNamespace,
    Material wireMaterial,
    Material storageCarrier,
    Material terminalCarrier,
    Material monitorCarrier,
    Material busCarrier,
    Material relayCarrier,
    Material transmitterCarrier,
    Material chunkLoaderCarrier,
    String wireItemModel,
    String storageItemModel,
    String terminalItemModel,
    String craftingTerminalItemModel,
    String monitorItemModel,
    String importBusItemModel,
    String exportBusItemModel,
    String relayItemModel,
    String transmitterItemModel,
    String chunkLoaderItemModel,
    String personalChunkLoaderItemModel,
    String dormantChunkLoaderItemModel,
    String wirelessItemModel,
    String wirelessDisabledModel,
    Map<WirelessBoosterTier, String> wirelessBoosterItemModels) {
  private static final String VANILLA_NAMESPACE = "minecraft";

  public RuntimeItemModelConfig {
    wirelessBoosterItemModels =
        wirelessBoosterItemModels == null ? Map.of() : Map.copyOf(wirelessBoosterItemModels);
  }

  public String wirelessBoosterItemModel(WirelessBoosterTier tier) {
    if (tier == null) {
      return normalizeModelId("amethyst_shard", VANILLA_NAMESPACE);
    }
    return wirelessBoosterItemModels.getOrDefault(
        tier, normalizeModelId("amethyst_shard", VANILLA_NAMESPACE));
  }

  public CustomItemModelConfig customItemModels() {
    return new CustomItemModelConfig(
        wireItemModel,
        storageItemModel,
        terminalItemModel,
        craftingTerminalItemModel,
        monitorItemModel,
        importBusItemModel,
        exportBusItemModel,
        relayItemModel,
        transmitterItemModel,
        chunkLoaderItemModel,
        personalChunkLoaderItemModel,
        dormantChunkLoaderItemModel,
        wirelessItemModel,
        wirelessDisabledModel,
        VANILLA_NAMESPACE + ":target",
        wirelessBoosterItemModels);
  }

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
        normalizeModelId("relay/relay", resourceNamespace),
        normalizeModelId("transmitter/transmitter", resourceNamespace),
        normalizeModelId("chunkloader/immortal", resourceNamespace),
        normalizeModelId("chunkloader/mythical", resourceNamespace),
        normalizeModelId("chunkloader/legendary", resourceNamespace),
        normalizeModelId("terminal/wireless", resourceNamespace),
        normalizeModelId("terminal/wireless_disabled", resourceNamespace),
        boosterModels(resourceNamespace, true));
  }

  private static RuntimeItemModelConfig vanillaConfig() {
    return new RuntimeItemModelConfig(
        VANILLA_NAMESPACE,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
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
        normalizeModelId("lodestone", VANILLA_NAMESPACE),
        normalizeModelId("lodestone", VANILLA_NAMESPACE),
        normalizeModelId("respawn_anchor", VANILLA_NAMESPACE),
        normalizeModelId("respawn_anchor", VANILLA_NAMESPACE),
        normalizeModelId("respawn_anchor", VANILLA_NAMESPACE),
        normalizeModelId("target", VANILLA_NAMESPACE),
        normalizeModelId("target", VANILLA_NAMESPACE),
        boosterModels(VANILLA_NAMESPACE, false));
  }

  private static Map<WirelessBoosterTier, String> boosterModels(
      String namespace, boolean resourceMode) {
    EnumMap<WirelessBoosterTier, String> result = new EnumMap<>(WirelessBoosterTier.class);
    for (WirelessBoosterTier tier : WirelessBoosterTier.values()) {
      result.put(
          tier,
          normalizeModelId(resourceMode ? tier.resourceModelId() : "amethyst_shard", namespace));
    }
    return Map.copyOf(result);
  }
}
