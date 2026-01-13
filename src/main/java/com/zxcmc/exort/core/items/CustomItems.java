package com.zxcmc.exort.core.items;

import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CustomItems {
  private static final Material BASE_MATERIAL = Material.PAPER;
  private final StorageKeys keys;
  private final Lang lang;
  private final String wireItemModel;
  private final String storageItemModel;
  private final String terminalItemModel;
  private final String craftingTerminalItemModel;
  private final String monitorItemModel;
  private final String importBusItemModel;
  private final String exportBusItemModel;
  private final String wirelessItemModel;
  private final String wirelessDisabledItemModel;
  private final String wirelessVanillaModel;

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
      String wirelessItemModel,
      String wirelessDisabledItemModel,
      String wirelessVanillaModel) {
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
    this.wirelessItemModel = wirelessItemModel == null ? "" : wirelessItemModel;
    this.wirelessDisabledItemModel =
        wirelessDisabledItemModel == null ? "" : wirelessDisabledItemModel;
    this.wirelessVanillaModel = wirelessVanillaModel == null ? "" : wirelessVanillaModel;
  }

  public ItemStack storageItem(StorageTier tier, String storageId) {
    return storageItem(tier, storageId, 0);
  }

  public ItemStack storageItem(StorageTier tier, String storageId, long currentAmount) {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(Component.text(tier.displayName()).decoration(TextDecoration.ITALIC, false));
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "storage");
      pdc.set(keys.storageTier(), PersistentDataType.STRING, tier.key());
      if (storageId != null) {
        pdc.set(keys.storageId(), PersistentDataType.STRING, storageId);
      }
      pdc.set(keys.nestedCount(), PersistentDataType.LONG, currentAmount);
      applyLore(meta, tier, currentAmount);
      ItemModelUtil.applyItemModel(meta, storageItemModel);
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack storageCoreItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(
          Component.text(lang.tr("item.storage_core")).decoration(TextDecoration.ITALIC, false));
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "storage_core");
      ItemModelUtil.applyItemModel(meta, storageItemModel);
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack terminalItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(
          Component.text(lang.tr("item.terminal")).decoration(TextDecoration.ITALIC, false));
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "terminal");
      ItemModelUtil.applyItemModel(meta, terminalItemModel);
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack craftingTerminalItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(
          Component.text(lang.tr("item.crafting_terminal"))
              .decoration(TextDecoration.ITALIC, false));
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "crafting_terminal");
      ItemModelUtil.applyItemModel(meta, craftingTerminalItemModel);
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack wireItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(Component.text(lang.tr("item.wire")).decoration(TextDecoration.ITALIC, false));
      ItemModelUtil.applyItemModel(meta, wireItemModel);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "wire");
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack monitorItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(
          Component.text(lang.tr("item.monitor")).decoration(TextDecoration.ITALIC, false));
      ItemModelUtil.applyItemModel(meta, monitorItemModel);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "monitor");
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack importBusItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(
          Component.text(lang.tr("item.import_bus")).decoration(TextDecoration.ITALIC, false));
      ItemModelUtil.applyItemModel(meta, importBusItemModel);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "import_bus");
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack exportBusItem() {
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(
          Component.text(lang.tr("item.export_bus")).decoration(TextDecoration.ITALIC, false));
      ItemModelUtil.applyItemModel(meta, exportBusItemModel);
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "export_bus");
      item.setItemMeta(meta);
    }
    return item;
  }

  public ItemStack wirelessTerminalItem(String owner, int charge) {
    ItemStack item = new ItemStack(Material.SHIELD);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(
          Component.text(lang.tr("item.wireless_terminal"))
              .decoration(TextDecoration.ITALIC, false));
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      pdc.set(keys.type(), PersistentDataType.STRING, "wireless_terminal");
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
        String tierRaw = pdc.get(keys.storageTier(), PersistentDataType.STRING);
        StorageTier tier = StorageTier.fromString(tierRaw).orElse(null);
        if (tier == null) return false;
        long nested = pdc.getOrDefault(keys.nestedCount(), PersistentDataType.LONG, 0L);
        meta.itemName(Component.text(tier.displayName()).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        applyLore(meta, tier, nested);
        ItemModelUtil.applyItemModel(meta, storageItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "storage_core" -> {
        meta.itemName(
            Component.text(lang.tr("item.storage_core")).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        ItemModelUtil.applyItemModel(meta, storageItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "terminal" -> {
        meta.itemName(
            Component.text(lang.tr("item.terminal")).decoration(TextDecoration.ITALIC, false));
        ItemModelUtil.applyItemModel(meta, terminalItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "crafting_terminal" -> {
        meta.itemName(
            Component.text(lang.tr("item.crafting_terminal"))
                .decoration(TextDecoration.ITALIC, false));
        ItemModelUtil.applyItemModel(meta, craftingTerminalItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "wire" -> {
        meta.itemName(
            Component.text(lang.tr("item.wire")).decoration(TextDecoration.ITALIC, false));
        ItemModelUtil.applyItemModel(meta, wireItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "monitor" -> {
        meta.itemName(
            Component.text(lang.tr("item.monitor")).decoration(TextDecoration.ITALIC, false));
        ItemModelUtil.applyItemModel(meta, monitorItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "import_bus" -> {
        meta.itemName(
            Component.text(lang.tr("item.import_bus")).decoration(TextDecoration.ITALIC, false));
        ItemModelUtil.applyItemModel(meta, importBusItemModel);
        stack.setItemMeta(meta);
        return true;
      }
      case "export_bus" -> {
        meta.itemName(
            Component.text(lang.tr("item.export_bus")).decoration(TextDecoration.ITALIC, false));
        ItemModelUtil.applyItemModel(meta, exportBusItemModel);
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

  public Optional<StorageTier> tierFromItem(ItemStack stack) {
    if (stack == null) return Optional.empty();
    if (!stack.hasItemMeta()) return Optional.empty();
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    if (!"storage".equalsIgnoreCase(type)) return Optional.empty();
    String tierRaw = pdc.get(keys.storageTier(), PersistentDataType.STRING);
    return StorageTier.fromString(tierRaw);
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
        stack
            .getItemMeta()
            .getPersistentDataContainer()
            .get(keys.storageId(), PersistentDataType.STRING));
  }

  private void applyLore(ItemMeta meta, StorageTier tier, long currentAmount) {
    long max = tier.maxItems();
    double percent =
        Math.min(100.0, Math.max(0.0, (double) currentAmount / Math.max(1, max) * 100.0));
    String line =
        lang.tr(
            "lore.storage.capacity",
            formatNumber(currentAmount),
            formatNumber(max),
            FORMAT_PERCENT.format(percent) + "%");
    List<Component> lore = new ArrayList<>();
    lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
    meta.lore(lore);
  }

  private String formatNumber(long value) {
    return FORMAT_NUMBER.format(value);
  }

  private static final DecimalFormat FORMAT_NUMBER = new DecimalFormat("#,###");
  private static final DecimalFormat FORMAT_PERCENT = new DecimalFormat("0.0");
}
