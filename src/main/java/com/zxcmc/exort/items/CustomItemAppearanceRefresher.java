package com.zxcmc.exort.items;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.i18n.StorageTierText;
import com.zxcmc.exort.i18n.WirelessBoosterText;
import com.zxcmc.exort.keys.PdcValueSanitizer;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageDisplayName;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.StorageTierResolver;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Rebuilds names, lore and models without owning item identity or creation. */
final class CustomItemAppearanceRefresher {
  private static final int ID_TAIL_LENGTH = 12;
  private static final DecimalFormat FORMAT_NUMBER = new DecimalFormat("#,###");
  private static final DecimalFormat FORMAT_PERCENT = new DecimalFormat("0.0");

  private final StorageKeys keys;
  private final Lang lang;
  private final CustomItemModelConfig models;
  private final WirelessRuntimeConfig wirelessConfig;
  private final boolean clientTranslations;

  CustomItemAppearanceRefresher(
      StorageKeys keys,
      Lang lang,
      CustomItemModelConfig models,
      WirelessRuntimeConfig wirelessConfig,
      boolean clientTranslations) {
    this.keys = keys;
    this.lang = lang;
    this.models = models;
    this.wirelessConfig = wirelessConfig;
    this.clientTranslations = clientTranslations;
  }

  boolean refresh(ItemStack stack, WirelessTerminalService wirelessService, boolean inStorage) {
    if (stack == null || !stack.hasItemMeta()) return false;
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    String type = CustomItemClassifier.recognizedType(keys, stack);
    if (type == null) return false;
    return switch (type) {
      case "storage" -> refreshStorage(stack, meta, pdc);
      case "storage_core" -> fixed(stack, meta, "item.storage_core", models.storage(), true);
      case "terminal" -> fixed(stack, meta, "item.terminal", models.terminal(), false);
      case "crafting_terminal" ->
          fixed(stack, meta, "item.crafting_terminal", models.craftingTerminal(), false);
      case "wire" -> fixed(stack, meta, "item.wire", models.wire(), false);
      case "monitor" -> fixed(stack, meta, "item.monitor", models.monitor(), false);
      case "import_bus" -> fixed(stack, meta, "item.import_bus", models.importBus(), false);
      case "export_bus" -> fixed(stack, meta, "item.export_bus", models.exportBus(), false);
      case "relay" -> fixed(stack, meta, "item.relay", models.relay(), false);
      case "transmitter" -> fixed(stack, meta, "item.transmitter", models.transmitter(), false);
      case "chunk_loader", "personal_chunk_loader", "dormant_chunk_loader" ->
          refreshChunkLoader(stack, meta, pdc, type);
      case "wireless_terminal" ->
          wirelessService != null && wirelessService.refreshAppearance(stack, inStorage);
      case "wireless_booster" -> refreshBooster(stack, meta, pdc);
      default -> false;
    };
  }

  private boolean refreshStorage(ItemStack stack, ItemMeta meta, PersistentDataContainer pdc) {
    StorageTierResolver.Resolution resolution =
        StorageTierResolver.resolve(
                pdc.get(keys.storageTier(), PersistentDataType.STRING),
                pdc.get(keys.storageTierMaxItems(), PersistentDataType.LONG))
            .orElse(null);
    if (resolution == null) return false;
    StorageTier tier = resolution.tier();
    pdc.set(keys.storageTier(), PersistentDataType.STRING, tier.key());
    pdc.set(keys.storageTierMaxItems(), PersistentDataType.LONG, resolution.tierMaxItems());
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
    meta.lore(storageLore(tier, storageId, nested));
    ItemModelUtil.applyItemModel(meta, models.storage());
    stack.setItemMeta(meta);
    return true;
  }

  private boolean fixed(
      ItemStack stack, ItemMeta meta, String translationKey, String model, boolean hideAttributes) {
    meta.itemName(lang.itemComponent(clientTranslations, translationKey));
    if (hideAttributes) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    ItemModelUtil.applyItemModel(meta, model);
    stack.setItemMeta(meta);
    return true;
  }

  private boolean refreshChunkLoader(
      ItemStack stack, ItemMeta meta, PersistentDataContainer pdc, String type) {
    ChunkLoaderType loaderType =
        ChunkLoaderType.fromNullableId(type).orElse(ChunkLoaderType.defaultType());
    pdc.set(keys.type(), PersistentDataType.STRING, loaderType.id());
    meta.itemName(
        CustomItemText.chunkLoaderName(
            loaderType, lang.itemComponent(clientTranslations, loaderType.translationKey())));
    String rawId =
        PdcValueSanitizer.uuidString(pdc.get(keys.chunkLoaderId(), PersistentDataType.STRING));
    UUID id = null;
    if (rawId != null) {
      try {
        id = UUID.fromString(rawId);
      } catch (IllegalArgumentException ignored) {
        id = null;
      }
    }
    meta.lore(chunkLoaderLore(id));
    ItemModelUtil.applyItemModel(meta, chunkLoaderModel(loaderType));
    stack.setItemMeta(meta);
    return true;
  }

  private boolean refreshBooster(ItemStack stack, ItemMeta meta, PersistentDataContainer pdc) {
    if (!CustomItemClassifier.isType(keys, stack, FixedItemCatalog.WIRELESS_BOOSTER.id())) {
      return false;
    }
    WirelessBoosterTier tier =
        WirelessBoosterTier.fromId(pdc.get(keys.wirelessBoosterTier(), PersistentDataType.STRING))
            .orElse(null);
    if (tier == null) return false;
    pdc.set(keys.wirelessBoosterTier(), PersistentDataType.STRING, tier.id());
    meta.itemName(
        lang.itemComponent(clientTranslations, "item.wireless_booster")
            .color(tier.color())
            .decoration(TextDecoration.ITALIC, false));
    meta.lore(WirelessBoosterText.lore(lang, clientTranslations, wirelessConfig, tier));
    ItemModelUtil.applyItemModel(
        meta, models.wirelessBoosters().getOrDefault(tier, "minecraft:amethyst_shard"));
    stack.setItemMeta(meta);
    return true;
  }

  private List<Component> storageLore(StorageTier tier, String storageId, long currentAmount) {
    long max = tier.maxItems();
    double percent =
        Math.min(100.0, Math.max(0.0, (double) currentAmount / Math.max(1, max) * 100.0));
    List<Component> lore = new ArrayList<>();
    lore.add(
        lang.itemComponent(
            clientTranslations,
            "lore.storage.capacity",
            FORMAT_NUMBER.format(currentAmount),
            FORMAT_NUMBER.format(max),
            FORMAT_PERCENT.format(percent) + "%"));
    if (storageId != null && storageId.length() >= ID_TAIL_LENGTH) {
      lore.add(
          lang.itemComponent(
                  clientTranslations,
                  "lore.storage.id_tail",
                  storageId.substring(storageId.length() - ID_TAIL_LENGTH))
              .color(NamedTextColor.GRAY)
              .decoration(TextDecoration.ITALIC, false));
    }
    lore.add(StorageTierText.tierValueLore(lang, clientTranslations, tier));
    return List.copyOf(lore);
  }

  private List<Component> chunkLoaderLore(UUID id) {
    if (id == null) return null;
    String raw = id.toString();
    return List.of(
        lang.itemComponent(
                clientTranslations,
                "lore.chunk_loader.id_tail",
                raw.substring(Math.max(0, raw.length() - ID_TAIL_LENGTH)))
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
  }

  private String chunkLoaderModel(ChunkLoaderType type) {
    return switch (type) {
      case PERSONAL_CHUNK_LOADER -> models.personalChunkLoader();
      case DORMANT_CHUNK_LOADER -> models.dormantChunkLoader();
      case CHUNK_LOADER -> models.chunkLoader();
    };
  }
}
