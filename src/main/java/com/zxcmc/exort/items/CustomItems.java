package com.zxcmc.exort.items;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.keys.PdcValueSanitizer;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageNameNormalizer;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.storage.StorageTierResolver;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CustomItems {
  private final StorageKeys keys;
  private final Lang lang;
  private final CustomItemFactory factory;
  private final CustomItemAppearanceRefresher appearanceRefresher;
  private final WirelessRuntimeConfig wirelessConfig;
  private final StorageTierCatalog storageTiers;
  private final boolean clientTranslations;

  public CustomItems(
      StorageKeys keys,
      Lang lang,
      CustomItemModelConfig models,
      WirelessRuntimeConfig wirelessConfig,
      boolean clientTranslations) {
    this(keys, lang, models, wirelessConfig, clientTranslations, StorageTierCatalog.active());
  }

  public CustomItems(
      StorageKeys keys,
      Lang lang,
      CustomItemModelConfig models,
      WirelessRuntimeConfig wirelessConfig,
      boolean clientTranslations,
      StorageTierCatalog storageTiers) {
    CustomItemModelConfig safeModels = models == null ? CustomItemModelConfig.empty() : models;
    this.keys = keys;
    this.lang = lang;
    this.wirelessConfig =
        wirelessConfig == null ? WirelessRuntimeConfig.defaults() : wirelessConfig;
    this.clientTranslations = clientTranslations;
    this.storageTiers = Objects.requireNonNull(storageTiers, "storageTiers");
    this.factory =
        keys == null || lang == null
            ? null
            : new CustomItemFactory(
                keys, lang, safeModels, this.wirelessConfig, clientTranslations);
    this.appearanceRefresher =
        new CustomItemAppearanceRefresher(
            keys, lang, safeModels, this.wirelessConfig, clientTranslations, this.storageTiers);
  }

  public ItemStack storageItem(StorageTier tier, String storageId) {
    return storageItem(tier, storageId, 0, null);
  }

  public ItemStack storageItem(StorageTier tier, String storageId, long currentAmount) {
    return storageItem(tier, storageId, currentAmount, null);
  }

  public ItemStack storageItem(
      StorageTier tier, String storageId, long currentAmount, String displayName) {
    return factory().storage(tier, storageId, currentAmount, displayName);
  }

  public ItemStack storageCoreItem() {
    return factory().fixed(FixedItemCatalog.STORAGE_CORE, true);
  }

  public ItemStack terminalItem() {
    return fixed(FixedItemCatalog.TERMINAL);
  }

  public ItemStack craftingTerminalItem() {
    return fixed(FixedItemCatalog.CRAFTING_TERMINAL);
  }

  public ItemStack wireItem() {
    return fixed(FixedItemCatalog.WIRE);
  }

  public ItemStack monitorItem() {
    return fixed(FixedItemCatalog.MONITOR);
  }

  public ItemStack importBusItem() {
    return fixed(FixedItemCatalog.IMPORT_BUS);
  }

  public ItemStack exportBusItem() {
    return fixed(FixedItemCatalog.EXPORT_BUS);
  }

  public ItemStack relayItem() {
    return fixed(FixedItemCatalog.RELAY);
  }

  public ItemStack transmitterItem() {
    return fixed(FixedItemCatalog.TRANSMITTER);
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
    return factory().chunkLoader(type, id);
  }

  public ItemStack wirelessTerminalItem(String owner, int charge) {
    return factory().wirelessTerminal(owner, charge);
  }

  public ItemStack wirelessBoosterItem(WirelessBoosterTier tier) {
    return factory().wirelessBooster(tier);
  }

  public boolean isCustomItem(ItemStack stack) {
    return CustomItemClassifier.isCustomItem(keys, stack);
  }

  public boolean refreshItem(
      ItemStack stack, WirelessTerminalService wirelessService, boolean inStorage) {
    return appearanceRefresher.refresh(stack, wirelessService, inStorage);
  }

  public boolean isWirelessTerminal(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.WIRELESS_TERMINAL.id());
  }

  public boolean isWirelessBooster(ItemStack stack) {
    return wirelessBoosterTier(stack).isPresent();
  }

  public Optional<WirelessBoosterTier> wirelessBoosterTier(ItemStack stack) {
    if (!CustomItemClassifier.isType(keys, stack, FixedItemCatalog.WIRELESS_BOOSTER.id())) {
      return Optional.empty();
    }
    String raw =
        stack
            .getItemMeta()
            .getPersistentDataContainer()
            .get(keys.wirelessBoosterTier(), PersistentDataType.STRING);
    return WirelessBoosterTier.fromId(raw);
  }

  public boolean isRelay(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.RELAY.id());
  }

  public boolean isTransmitter(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.TRANSMITTER.id());
  }

  public boolean isChunkLoader(ItemStack stack) {
    String type = CustomItemClassifier.recognizedType(keys, stack);
    return ChunkLoaderType.isChunkLoaderId(type);
  }

  public boolean isStorageCore(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.STORAGE_CORE.id());
  }

  public void applyWirelessModel(ItemMeta meta, boolean charged, boolean linked, boolean enabled) {
    factory().applyWirelessModel(meta, charged, linked, enabled);
  }

  public boolean clientTranslations() {
    return clientTranslations;
  }

  public WirelessRuntimeConfig wirelessConfig() {
    return wirelessConfig;
  }

  public Optional<StorageTier> tierFromItem(ItemStack stack) {
    if (!CustomItemClassifier.isType(keys, stack, "storage")) return Optional.empty();
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    return resolveStorageTier(pdc).map(StorageTierResolver.Resolution::tier);
  }

  public boolean isTerminal(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.TERMINAL.id());
  }

  public boolean isCraftingTerminal(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.CRAFTING_TERMINAL.id());
  }

  public boolean isAnyTerminal(ItemStack stack) {
    return isTerminal(stack) || isCraftingTerminal(stack);
  }

  public boolean isMonitor(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.MONITOR.id());
  }

  public boolean isImportBus(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.IMPORT_BUS.id());
  }

  public boolean isExportBus(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.EXPORT_BUS.id());
  }

  public boolean isAnyBus(ItemStack stack) {
    return isImportBus(stack) || isExportBus(stack);
  }

  public boolean isWire(ItemStack stack) {
    return CustomItemClassifier.isType(keys, stack, FixedItemCatalog.WIRE.id());
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
    String rawName = StorageNameNormalizer.normalizeAnvilInput(lang, displayName);
    if (!StorageItemNameEditor.apply(keys, stack, rawName)) {
      return false;
    }
    return refreshItem(stack, null, false);
  }

  private ItemStack fixed(CustomItemIdentity identity) {
    return factory().fixed(identity, false);
  }

  private CustomItemFactory factory() {
    if (factory == null) {
      throw new IllegalStateException("Custom item factory is not initialized");
    }
    return factory;
  }

  private Optional<StorageTierResolver.Resolution> resolveStorageTier(PersistentDataContainer pdc) {
    String tierRaw = pdc.get(keys.storageTier(), PersistentDataType.STRING);
    Long tierMaxItems = pdc.get(keys.storageTierMaxItems(), PersistentDataType.LONG);
    return StorageTierResolver.resolve(storageTiers, tierRaw, tierMaxItems);
  }
}
