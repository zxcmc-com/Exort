package com.zxcmc.exort.items;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.i18n.StorageTierText;
import com.zxcmc.exort.i18n.WirelessBoosterText;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageDisplayName;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

/**
 * Creates fixed and dynamic Exort items while keeping identity and appearance initialization
 * atomic.
 */
final class CustomItemFactory {
  private static final Material BASE_MATERIAL = Material.PAPER;
  private static final int ID_TAIL_LENGTH = 12;

  private final StorageKeys keys;
  private final Lang lang;
  private final CustomItemModelConfig models;
  private final WirelessRuntimeConfig wirelessConfig;
  private final boolean clientTranslations;

  CustomItemFactory(
      StorageKeys keys,
      Lang lang,
      CustomItemModelConfig models,
      WirelessRuntimeConfig wirelessConfig,
      boolean clientTranslations) {
    this.keys = Objects.requireNonNull(keys, "keys");
    this.lang = Objects.requireNonNull(lang, "lang");
    this.models = Objects.requireNonNull(models, "models");
    this.wirelessConfig = Objects.requireNonNull(wirelessConfig, "wirelessConfig");
    this.clientTranslations = clientTranslations;
  }

  ItemStack storage(StorageTier tier, String storageId, long currentAmount, String displayName) {
    Objects.requireNonNull(tier, "tier");
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.itemName(StorageTierText.storageName(lang, clientTranslations, tier));
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(keys.type(), PersistentDataType.STRING, "storage");
    pdc.set(keys.storageTier(), PersistentDataType.STRING, tier.key());
    pdc.set(keys.storageTierMaxItems(), PersistentDataType.LONG, tier.maxItems());
    if (storageId != null) pdc.set(keys.storageId(), PersistentDataType.STRING, storageId);
    pdc.set(keys.nestedCount(), PersistentDataType.LONG, currentAmount);
    StorageItemNameEditor.apply(
        keys,
        meta,
        pdc,
        displayName,
        StorageDisplayName.customNameComponent(lang, clientTranslations, tier, displayName));
    meta.lore(storageLore(tier, storageId, currentAmount));
    ItemModelUtil.applyItemModel(meta, models.storage());
    item.setItemMeta(meta);
    return item;
  }

  ItemStack fixed(CustomItemIdentity identity, String model, boolean hideAttributes) {
    Objects.requireNonNull(identity, "identity");
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.itemName(lang.itemComponent(clientTranslations, identity.translationKey()));
    if (hideAttributes) {
      meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    }
    meta.getPersistentDataContainer().set(keys.type(), PersistentDataType.STRING, identity.id());
    ItemModelUtil.applyItemModel(meta, model);
    item.setItemMeta(meta);
    return item;
  }

  ItemStack fixed(CustomItemIdentity identity, boolean hideAttributes) {
    return fixed(identity, model(identity), hideAttributes);
  }

  ItemStack chunkLoader(ChunkLoaderType type, UUID id) {
    ChunkLoaderType safeType = type == null ? ChunkLoaderType.defaultType() : type;
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.itemName(
        CustomItemText.chunkLoaderName(
            safeType, lang.itemComponent(clientTranslations, safeType.translationKey())));
    ItemModelUtil.applyItemModel(meta, chunkLoaderModel(safeType));
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(keys.type(), PersistentDataType.STRING, safeType.id());
    if (id != null) pdc.set(keys.chunkLoaderId(), PersistentDataType.STRING, id.toString());
    meta.lore(chunkLoaderLore(id));
    item.setItemMeta(meta);
    return item;
  }

  ItemStack wirelessTerminal(String owner, int charge) {
    ItemStack item = new ItemStack(Material.SHIELD);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.itemName(lang.itemComponent(clientTranslations, "item.wireless_terminal"));
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(keys.type(), PersistentDataType.STRING, FixedItemCatalog.WIRELESS_TERMINAL.id());
    if (owner != null) pdc.set(keys.wirelessOwner(), PersistentDataType.STRING, owner);
    int effectiveCharge = Math.max(0, Math.min(100, charge));
    pdc.set(keys.wirelessCharge(), PersistentDataType.INTEGER, effectiveCharge);
    applyWirelessModel(meta, effectiveCharge > 0, false, true);
    item.setItemMeta(meta);
    return item;
  }

  ItemStack wirelessBooster(WirelessBoosterTier tier) {
    Objects.requireNonNull(tier, "tier");
    ItemStack item = new ItemStack(BASE_MATERIAL);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return item;
    meta.itemName(
        lang.itemComponent(clientTranslations, "item.wireless_booster")
            .color(tier.color())
            .decoration(TextDecoration.ITALIC, false));
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(keys.type(), PersistentDataType.STRING, FixedItemCatalog.WIRELESS_BOOSTER.id());
    pdc.set(keys.wirelessBoosterTier(), PersistentDataType.STRING, tier.id());
    meta.lore(WirelessBoosterText.lore(lang, clientTranslations, wirelessConfig, tier));
    ItemModelUtil.applyItemModel(
        meta, models.wirelessBoosters().getOrDefault(tier, "minecraft:amethyst_shard"));
    item.setItemMeta(meta);
    return item;
  }

  void applyWirelessModel(ItemMeta meta, boolean charged, boolean linked, boolean enabled) {
    if (meta == null) return;
    String model;
    if (!enabled || !charged || !linked) {
      model =
          models.wirelessDisabled().isEmpty()
              ? models.wirelessVanilla()
              : models.wirelessDisabled();
    } else {
      model = models.wireless().isEmpty() ? models.wirelessVanilla() : models.wireless();
    }
    ItemModelUtil.applyItemModel(meta, model);
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
            String.format(Locale.ROOT, "%,d", currentAmount),
            String.format(Locale.ROOT, "%,d", max),
            String.format(Locale.ROOT, "%.1f%%", percent)));
    if (storageId != null && !storageId.isBlank() && storageId.length() >= ID_TAIL_LENGTH) {
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

  private String model(CustomItemIdentity identity) {
    return switch (identity.id()) {
      case "storage_core" -> models.storage();
      case "terminal" -> models.terminal();
      case "crafting_terminal" -> models.craftingTerminal();
      case "wire" -> models.wire();
      case "monitor" -> models.monitor();
      case "import_bus" -> models.importBus();
      case "export_bus" -> models.exportBus();
      case "relay" -> models.relay();
      case "transmitter" -> models.transmitter();
      default -> throw new IllegalArgumentException("Unsupported fixed item: " + identity.id());
    };
  }
}
