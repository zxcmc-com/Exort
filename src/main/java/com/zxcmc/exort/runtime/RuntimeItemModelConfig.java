package com.zxcmc.exort.runtime;

import com.zxcmc.exort.carrier.Carriers;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

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

  public static RuntimeItemModelConfig fromConfig(
      ConfigurationSection config, boolean resourceMode) {
    Objects.requireNonNull(config, "config");
    return resourceMode ? resourceConfig(config) : vanillaConfig(config);
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

  private static RuntimeItemModelConfig resourceConfig(ConfigurationSection config) {
    String resourceNamespace = config.getString("resourceMode.namespace", "exort");
    return new RuntimeItemModelConfig(
        resourceNamespace,
        Carriers.CHORUS_MATERIAL,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        normalizeModelId(
            config.getString("resourceMode.wire.itemModel", "wire/center"), resourceNamespace),
        normalizeModelId(
            config.getString("resourceMode.storage.itemModel", "storage/storage"),
            resourceNamespace),
        normalizeModelId(
            config.getString("resourceMode.terminal.itemModel", "terminal/inventory"),
            resourceNamespace),
        normalizeModelId(
            config.getString("resourceMode.craftingTerminal.itemModel", "terminal/workbench"),
            resourceNamespace),
        normalizeModelId(
            config.getString("resourceMode.monitor.itemModel", "terminal/monitor"),
            resourceNamespace),
        normalizeModelId(
            config.getString("resourceMode.bus.import.itemModel", "bus/import"), resourceNamespace),
        normalizeModelId(
            config.getString("resourceMode.bus.export.itemModel", "bus/export"), resourceNamespace),
        normalizeModelId(
            config.getString("resourceMode.wirelessTerminal.itemModel", "terminal/wireless"),
            resourceNamespace),
        normalizeModelId(
            config.getString(
                "resourceMode.wirelessTerminal.modelDisabledId", "terminal/wireless_disabled"),
            resourceNamespace));
  }

  private static RuntimeItemModelConfig vanillaConfig(ConfigurationSection config) {
    return new RuntimeItemModelConfig(
        VANILLA_NAMESPACE,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        normalizeModelId(
            config.getString("vanillaMode.wire.modelId", "black_stained_glass"), VANILLA_NAMESPACE),
        normalizeModelId(
            config.getString("vanillaMode.storage.modelId", "vault"), VANILLA_NAMESPACE),
        normalizeModelId(
            config.getString("vanillaMode.terminal.modelId", "barrel"), VANILLA_NAMESPACE),
        normalizeModelId(
            config.getString("vanillaMode.craftingTerminal.modelId", "crafting_table"),
            VANILLA_NAMESPACE),
        normalizeModelId(
            config.getString("vanillaMode.monitor.itemModel", "smooth_stone"), VANILLA_NAMESPACE),
        normalizeModelId(
            config.getString("vanillaMode.importBus.itemModel", "dispenser"), VANILLA_NAMESPACE),
        normalizeModelId(
            config.getString("vanillaMode.exportBus.itemModel", "dropper"), VANILLA_NAMESPACE),
        normalizeModelId(
            config.getString("vanillaMode.wirelessTerminal.modelId", "target"), VANILLA_NAMESPACE),
        normalizeModelId(
            config.getString("vanillaMode.wirelessTerminal.modelDisabledId", "target"),
            VANILLA_NAMESPACE));
  }
}
