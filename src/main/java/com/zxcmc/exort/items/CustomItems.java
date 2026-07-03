package com.zxcmc.exort.items;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.i18n.StorageTierText;
import com.zxcmc.exort.keys.PdcValueSanitizer;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageDisplayName;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.StorageTierResolver;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CustomItems {
  private static final Material BASE_MATERIAL = Material.PAPER;
  private static final int STORAGE_ID_TAIL_LENGTH = 12;
  private final StorageKeys keys;
  private final Lang lang;
  private final String wireItemModel;
  private final String storageItemModel;
  private final String terminalItemModel;
  private final String craftingTerminalItemModel;
  private final String monitorItemModel;
  private final String importBusItemModel;
  private final String exportBusItemModel;
  private final String relayItemModel;
  private final String chunkLoaderItemModel;
  private final String personalChunkLoaderItemModel;
  private final String dormantChunkLoaderItemModel;
  private final String wirelessItemModel;
  private final String wirelessDisabledItemModel;
  private final String wirelessVanillaModel;
  private final boolean clientTranslations;

  public CustomItems(
      StorageKeys keys,
      Lang lang,
      String wireItemModel,
      String storageItemModel,
      String terminalItemModel,
      String craftingTerminalItemModel,
      String monitorItemModel,
      String importBusItemModel,
      String exportBusItemModel,
      String relayItemModel,
      String chunkLoaderItemModel,
      String personalChunkLoaderItemModel,
      String dormantChunkLoaderItemModel,
      String wirelessItemModel,
      String wirelessDisabledItemModel,
      String wirelessVanillaModel,
      boolean clientTranslations) {
    this.keys = keys;
    this.lang = lang;
    this.wireItemModel = wireItemModel == null ? "" : wireItemModel;
    this.storageItemModel = storageItemModel == null ? "" : storageItemModel;
    this.terminalItemModel = terminalItemModel == null ? "" : terminalItemModel;
    this.craftingTerminalItemModel =
        craftingTerminalItemModel == null ? "" : craftingTerminalItemModel;
    this.monitorItemModel = monitorItemModel == null ? "" : monitorItemModel;
    this.importBusItemModel = importBusItemModel == null ? "" : importBusItemModel;
    this.exportBusItemModel = exportBusItemModel == null ? "" : exportBusItemModel;
    this.relayItemModel = relayItemModel == null ? "" : relayItemModel;
    this.chunkLoaderItemModel = chunkLoaderItemModel == null ? "" : chunkLoaderItemModel;
    this.personalChunkLoaderItemModel =
        personalChunkLoaderItemModel == null ? "" : personalChunkLoaderItemModel;
    this.dormantChunkLoaderItemModel =
        dormantChunkLoaderItemModel == null ? "" : dormantChunkLoaderItemModel;
    this.wirelessItemModel = wirelessItemModel == null ? "" : wirelessItemModel;
    this.wirelessDisabledItemModel =
        wirelessDisabledItemModel == null ? "" : wirelessDisabledItemModel;
    this.wirelessVanillaModel = wirelessVanillaModel == null ? "" : wirelessVanillaModel;
    this.clientTranslations = clientTranslations;
  }

  public ItemStack storageItem(StorageTier tier, String storageId) {
    return storageItem(tier, storageId, 0, null);
  }

  public ItemStack storageItem(StorageTier tier, String storageId, long currentAmount) {
    return storageItem(tier, storageId, currentAmount, null);
  }

  public ItemStack storageItem(
      StorageTier tier, String storageId, long currentAmount, String displayName) {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(StorageTierText.storageName(lang, clientTranslations, tier));
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "storage");
      pdc.set(keys.storageTier(), PersistentDataType.STRING, tier.key());
      pdc.set(keys.storageTierMaxItems(), PersistentDataType.LONG, tier.maxItems());
      if (storageId != null) {
        pdc.set(keys.storageId(), PersistentDataType.STRING, storageId);
      }
      pdc.set(keys.nestedCount(), PersistentDataType.LONG, currentAmount);
      StorageItemNameEditor.apply(
          keys,
          meta,
          pdc,
          displayName,
          StorageDisplayName.customNameComponent(lang, clientTranslations, tier, displayName));
      applyLore(meta, tier, storageId, currentAmount);
      ItemModelUtil.applyItemModel(meta, storageItemModel);
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack storageCoreItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(lang.itemComponent(clientTranslations, "item.storage_core"));
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, CustomItemRegistry.STORAGE_CORE.id());
      ItemModelUtil.applyItemModel(meta, storageItemModel);
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack terminalItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(lang.itemComponent(clientTranslations, "item.terminal"));
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, CustomItemRegistry.TERMINAL.id());
      ItemModelUtil.applyItemModel(meta, terminalItemModel);
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack craftingTerminalItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(lang.itemComponent(clientTranslations, "item.crafting_terminal"));
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, CustomItemRegistry.CRAFTING_TERMINAL.id());
      ItemModelUtil.applyItemModel(meta, craftingTerminalItemModel);
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack wireItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(lang.itemComponent(clientTranslations, "item.wire"));
      ItemModelUtil.applyItemModel(meta, wireItemModel);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, CustomItemRegistry.WIRE.id());
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack monitorItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(lang.itemComponent(clientTranslations, "item.monitor"));
      ItemModelUtil.applyItemModel(meta, monitorItemModel);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, CustomItemRegistry.MONITOR.id());
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack importBusItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(lang.itemComponent(clientTranslations, "item.import_bus"));
      ItemModelUtil.applyItemModel(meta, importBusItemModel);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, CustomItemRegistry.IMPORT_BUS.id());
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack exportBusItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(lang.itemComponent(clientTranslations, "item.export_bus"));
      ItemModelUtil.applyItemModel(meta, exportBusItemModel);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, CustomItemRegistry.EXPORT_BUS.id());
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack relayItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(lang.itemComponent(clientTranslations, "item.relay"));
      ItemModelUtil.applyItemModel(meta, relayItemModel);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, CustomItemRegistry.RELAY.id());
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack chunkLoaderItem() {
    return chunkLoaderItem(ChunkLoaderType.defaultType(), null);
  }

  public ItemStack chunkLoaderItem(UUID id) {
    return chunkLoaderItem(ChunkLoaderType.defaultType(), id);
  }

  public ItemStack chunkLoaderItem(ChunkLoaderType type) {
    return chunkLoaderItem(type, null);
  }

  public ItemStack chunkLoaderItem(ChunkLoaderType type, UUID id) {
    ChunkLoaderType safeType = type == null ? ChunkLoaderType.defaultType() : type;
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(chunkLoaderName(safeType));
      ItemModelUtil.applyItemModel(meta, chunkLoaderItemModel(safeType));
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, safeType.id());
      if (id != null) {
        pdc.set(keys.chunkLoaderId(), PersistentDataType.STRING, id.toString());
      }
      applyChunkLoaderLore(meta, id);
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack wirelessTerminalItem(String owner, int charge) {
    ItemStack item = new ItemStack(Material.SHIELD);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(lang.itemComponent(clientTranslations, "item.wireless_terminal"));
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, CustomItemRegistry.WIRELESS_TERMINAL.id());
      if (owner != null) {
        pdc.set(keys.wirelessOwner(), PersistentDataType.STRING, owner);
      }
      pdc.set(
          keys.wirelessCharge(), PersistentDataType.INTEGER, Math.max(0, Math.min(100, charge)));
      applyWirelessModel(meta, charge > 0, false, true);
      item.setItemMeta(meta);
    }
    return item;
  }

  public boolean isCustomItem(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    return pdc.has(keys.type(), PersistentDataType.STRING);
  }

  public boolean refreshItem(
      ItemStack stack, WirelessTerminalService wirelessService, boolean inStorage) {
    if (stack == null || !stack.hasItemMeta()) return false;
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    if (type == null) return false;
    switch (type) {
      case "storage" -> {
        StorageTierResolver.Resolution resolution = resolveStorageTier(pdc).orElse(null);
        if (resolution == null) return false;
        StorageTier tier = resolution.tier();
        String storageId = pdc.get(keys.storageId(), PersistentDataType.STRING);
        long nested = pdc.getOrDefault(keys.nestedCount(), PersistentDataType.LONG, 0L);
        String displayName = StorageItemNameEditor.displayName(keys, pdc).orElse(null);
        meta.itemName(StorageTierText.storageName(lang, clientTranslations, tier));
        StorageItemNameEditor.apply(
            keys,
            meta,
            pdc,
            displayName,
            StorageDisplayName.customNameComponent(lang, clientTranslations, tier, displayName));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        applyLore(meta, tier, storageId, nested);
        ItemModelUtil.applyItemModel(meta, storageItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "storage_core" -> {
        meta.itemName(lang.itemComponent(clientTranslations, "item.storage_core"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        ItemModelUtil.applyItemModel(meta, storageItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "terminal" -> {
        meta.itemName(lang.itemComponent(clientTranslations, "item.terminal"));
        ItemModelUtil.applyItemModel(meta, terminalItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "crafting_terminal" -> {
        meta.itemName(lang.itemComponent(clientTranslations, "item.crafting_terminal"));
        ItemModelUtil.applyItemModel(meta, craftingTerminalItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "wire" -> {
        meta.itemName(lang.itemComponent(clientTranslations, "item.wire"));
        ItemModelUtil.applyItemModel(meta, wireItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "monitor" -> {
        meta.itemName(lang.itemComponent(clientTranslations, "item.monitor"));
        ItemModelUtil.applyItemModel(meta, monitorItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "import_bus" -> {
        meta.itemName(lang.itemComponent(clientTranslations, "item.import_bus"));
        ItemModelUtil.applyItemModel(meta, importBusItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "export_bus" -> {
        meta.itemName(lang.itemComponent(clientTranslations, "item.export_bus"));
        ItemModelUtil.applyItemModel(meta, exportBusItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "relay" -> {
        meta.itemName(lang.itemComponent(clientTranslations, "item.relay"));
        ItemModelUtil.applyItemModel(meta, relayItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "chunk_loader", "personal_chunk_loader", "dormant_chunk_loader" -> {
        ChunkLoaderType loaderType = chunkLoaderType(stack);
        pdc.set(keys.type(), PersistentDataType.STRING, loaderType.id());
        meta.itemName(chunkLoaderName(loaderType));
        applyChunkLoaderLore(meta, chunkLoaderId(stack).orElse(null));
        ItemModelUtil.applyItemModel(meta, chunkLoaderItemModel(loaderType));
        stack.setItemMeta(meta);
        return true;
      }
      case "wireless_terminal" -> {
        if (wirelessService == null) return false;
        return wirelessService.refreshAppearance(stack, inStorage);
      }
      default -> {
        return false;
      }
    }
  }

  public boolean isWirelessTerminal(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "wireless_terminal".equalsIgnoreCase(type);
  }

  public boolean isRelay(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "relay".equalsIgnoreCase(type);
  }

  public boolean isChunkLoader(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return ChunkLoaderType.isChunkLoaderId(type);
  }

  public boolean isStorageCore(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "storage_core".equalsIgnoreCase(type);
  }

  public void applyWirelessModel(ItemMeta meta, boolean charged, boolean linked, boolean enabled) {
    if (meta == null) return;
    if (!enabled || !charged || !linked) {
      ItemModelUtil.applyItemModel(
          meta,
          wirelessDisabledItemModel.isEmpty() ? wirelessVanillaModel : wirelessDisabledItemModel);
    } else {
      ItemModelUtil.applyItemModel(
          meta, wirelessItemModel.isEmpty() ? wirelessVanillaModel : wirelessItemModel);
    }
  }

  public boolean clientTranslations() {
    return clientTranslations;
  }

  public Optional<StorageTier> tierFromItem(ItemStack stack) {
    if (stack == null) return Optional.empty();
    if (!stack.hasItemMeta()) return Optional.empty();
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    if (!"storage".equalsIgnoreCase(type)) return Optional.empty();
    Optional<StorageTierResolver.Resolution> resolution = resolveStorageTier(pdc);
    if (resolution.isPresent()) {
      stack.setItemMeta(meta);
    }
    return resolution.map(StorageTierResolver.Resolution::tier);
  }

  public boolean isTerminal(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "terminal".equalsIgnoreCase(type);
  }

  public boolean isCraftingTerminal(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "crafting_terminal".equalsIgnoreCase(type);
  }

  public boolean isAnyTerminal(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "terminal".equalsIgnoreCase(type) || "crafting_terminal".equalsIgnoreCase(type);
  }

  public boolean isMonitor(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "monitor".equalsIgnoreCase(type);
  }

  public boolean isImportBus(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "import_bus".equalsIgnoreCase(type);
  }

  public boolean isExportBus(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "export_bus".equalsIgnoreCase(type);
  }

  public boolean isAnyBus(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "import_bus".equalsIgnoreCase(type) || "export_bus".equalsIgnoreCase(type);
  }

  public Optional<String> storageId(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return Optional.empty();
    return Optional.ofNullable(
        PdcValueSanitizer.uuidString(
            stack
                .getItemMeta()
                .getPersistentDataContainer()
                .get(keys.storageId(), PersistentDataType.STRING)));
  }

  public Optional<UUID> chunkLoaderId(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return Optional.empty();
    String raw =
        PdcValueSanitizer.uuidString(
            stack
                .getItemMeta()
                .getPersistentDataContainer()
                .get(keys.chunkLoaderId(), PersistentDataType.STRING));
    if (raw == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(raw));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  public ChunkLoaderType chunkLoaderType(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return ChunkLoaderType.defaultType();
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    return ChunkLoaderType.fromNullableId(pdc.get(keys.type(), PersistentDataType.STRING))
        .orElse(ChunkLoaderType.defaultType());
  }

  public Optional<String> storageDisplayName(ItemStack stack) {
    return StorageItemNameEditor.displayName(keys, stack);
  }

  public boolean setStorageDisplayName(ItemStack stack, String displayName) {
    String rawName = StorageDisplayName.normalizeAnvilInput(lang, displayName);
    if (!StorageItemNameEditor.apply(keys, stack, rawName)) {
      return false;
    }
    return refreshItem(stack, null, false);
  }

  private Optional<StorageTierResolver.Resolution> resolveStorageTier(PersistentDataContainer pdc) {
    String tierRaw = pdc.get(keys.storageTier(), PersistentDataType.STRING);
    Long tierMaxItems = pdc.get(keys.storageTierMaxItems(), PersistentDataType.LONG);
    Optional<StorageTierResolver.Resolution> resolution =
        StorageTierResolver.resolve(tierRaw, tierMaxItems);
    resolution.ifPresent(
        resolved -> {
          pdc.set(keys.storageTier(), PersistentDataType.STRING, resolved.tier().key());
          pdc.set(keys.storageTierMaxItems(), PersistentDataType.LONG, resolved.tierMaxItems());
        });
    return resolution;
  }

  private void applyLore(ItemMeta meta, StorageTier tier, String storageId, long currentAmount) {
    long max = tier.maxItems();
    double percent =
        Math.min(100.0, Math.max(0.0, (double) currentAmount / Math.max(1, max) * 100.0));
    List<Component> lore = new ArrayList<>();
    lore.add(
        lang.itemComponent(
            clientTranslations,
            "lore.storage.capacity",
            formatNumber(currentAmount),
            formatNumber(max),
            FORMAT_PERCENT.format(percent) + "%"));
    if (storageId != null && !storageId.isBlank() && storageId.length() >= STORAGE_ID_TAIL_LENGTH) {
      String tail = storageId.substring(storageId.length() - STORAGE_ID_TAIL_LENGTH);
      lore.add(
          lang.itemComponent(clientTranslations, "lore.storage.id_tail", tail)
              .color(NamedTextColor.GRAY)
              .decoration(TextDecoration.ITALIC, false));
    }
    lore.add(StorageTierText.tierValueLore(lang, clientTranslations, tier));
    meta.lore(lore);
  }

  private void applyChunkLoaderLore(ItemMeta meta, UUID id) {
    if (id == null) {
      meta.lore(null);
      return;
    }
    String raw = id.toString();
    String tail = raw.substring(Math.max(0, raw.length() - STORAGE_ID_TAIL_LENGTH));
    meta.lore(
        List.of(
            lang.itemComponent(clientTranslations, "lore.chunk_loader.id_tail", tail)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
  }

  private Component chunkLoaderName(ChunkLoaderType type) {
    ChunkLoaderType safeType = type == null ? ChunkLoaderType.defaultType() : type;
    return CustomItemText.chunkLoaderName(
        safeType, lang.itemComponent(clientTranslations, safeType.translationKey()));
  }

  private String chunkLoaderItemModel(ChunkLoaderType type) {
    return switch (type == null ? ChunkLoaderType.defaultType() : type) {
      case PERSONAL_CHUNK_LOADER -> personalChunkLoaderItemModel;
      case DORMANT_CHUNK_LOADER -> dormantChunkLoaderItemModel;
      case CHUNK_LOADER -> chunkLoaderItemModel;
    };
  }

  private String formatNumber(long value) {
    return FORMAT_NUMBER.format(value);
  }

  private static final DecimalFormat FORMAT_NUMBER = new DecimalFormat("#,###");
  private static final DecimalFormat FORMAT_PERCENT = new DecimalFormat("0.0");
}
