package com.zxcmc.exort.command;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.infra.config.FeatureAccessConfig;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.items.FixedItemCatalog;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class ExortGiveMenu implements InventoryHolder {
  static final int SIZE = 54;
  static final String TITLE = "Exort Storage Network";
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String PERMISSION_GIVE = "exort.storagenetwork.give";

  private static final List<String> FIXED_ITEM_IDS = FixedItemCatalog.fixedItemIds();

  private final Inventory inventory;
  private final Supplier<CustomItems> customItems;
  private final Supplier<ItemStack> wirelessTerminalFactory;
  private final Supplier<FeatureAccessConfig> featureAccess;
  private final Supplier<StorageTierCatalog> storageTierCatalog;
  private final BiConsumer<Player, ItemStack> issueLogger;
  private final BiConsumer<Player, ItemStack> destroyLogger;

  public ExortGiveMenu(
      CustomItems customItems, Supplier<ItemStack> wirelessTerminalFactory, Component title) {
    this(() -> customItems, wirelessTerminalFactory, title);
  }

  public ExortGiveMenu(
      Supplier<CustomItems> customItems,
      Supplier<ItemStack> wirelessTerminalFactory,
      Component title) {
    this(customItems, wirelessTerminalFactory, title, (player, item) -> {});
  }

  public ExortGiveMenu(
      Supplier<CustomItems> customItems,
      Supplier<ItemStack> wirelessTerminalFactory,
      Component title,
      BiConsumer<Player, ItemStack> issueLogger) {
    this(customItems, wirelessTerminalFactory, title, issueLogger, (player, item) -> {});
  }

  public ExortGiveMenu(
      Supplier<CustomItems> customItems,
      Supplier<ItemStack> wirelessTerminalFactory,
      Component title,
      BiConsumer<Player, ItemStack> issueLogger,
      BiConsumer<Player, ItemStack> destroyLogger) {
    this(
        customItems,
        wirelessTerminalFactory,
        title,
        issueLogger,
        destroyLogger,
        FeatureAccessConfig::defaults,
        StorageTierCatalog::empty);
  }

  public ExortGiveMenu(
      Supplier<CustomItems> customItems,
      Supplier<ItemStack> wirelessTerminalFactory,
      Component title,
      BiConsumer<Player, ItemStack> issueLogger,
      BiConsumer<Player, ItemStack> destroyLogger,
      Supplier<FeatureAccessConfig> featureAccess,
      Supplier<StorageTierCatalog> storageTierCatalog) {
    this.customItems = Objects.requireNonNull(customItems, "customItems");
    this.wirelessTerminalFactory =
        Objects.requireNonNull(wirelessTerminalFactory, "wirelessTerminalFactory");
    this.featureAccess = featureAccess == null ? FeatureAccessConfig::defaults : featureAccess;
    this.storageTierCatalog = Objects.requireNonNull(storageTierCatalog, "storageTierCatalog");
    this.issueLogger = issueLogger == null ? (player, item) -> {} : issueLogger;
    this.destroyLogger = destroyLogger == null ? (player, item) -> {} : destroyLogger;
    inventory = Bukkit.createInventory(this, SIZE, title == null ? Component.text(TITLE) : title);
    refreshCatalog();
  }

  public void refreshCatalog() {
    List<ItemStack> items =
        catalogItems(
            currentCustomItems(),
            wirelessTerminalFactory,
            currentFeatureAccess(),
            currentStorageTierCatalog());
    validateCatalogSize(items.size());
    inventory.clear();
    for (int i = 0; i < items.size(); i++) {
      inventory.setItem(i, items.get(i));
    }
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  public void open(Player player) {
    player.openInventory(inventory);
  }

  public void handleClick(InventoryClickEvent event) {
    int topSize = event.getView().getTopInventory().getSize();
    int rawSlot = event.getRawSlot();
    ClickType click = event.getClick();
    boolean topSlot = rawSlot >= 0 && rawSlot < topSize;
    boolean unsafeBottomClick =
        rawSlot >= topSize && (event.isShiftClick() || click == ClickType.DOUBLE_CLICK);
    if (!topSlot && !unsafeBottomClick) {
      return;
    }

    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    if (!canMutate(player)) {
      player.closeInventory();
      return;
    }
    if (!topSlot) {
      destroyShiftClickedItem(event);
      return;
    }

    ItemStack cursor = event.getView().getCursor();
    if (!isEmpty(cursor)) {
      destroyCursorItem(event, cursor, click);
      return;
    }

    ItemStack sample = event.getCurrentItem();
    if (isEmpty(sample)) {
      return;
    }
    ItemStack copy = copyForDelivery(sample);
    if (event.isShiftClick()) {
      CommandItemDelivery.Result result =
          CommandItemDelivery.deliver(player, () -> copy.clone(), copy.getAmount());
      int deliveredAmount = result.total();
      if (result.undelivered() > 0 && isEmpty(event.getView().getCursor())) {
        ItemStack cursorFallback = copy.clone();
        cursorFallback.setAmount(result.undelivered());
        event.getView().setCursor(cursorFallback);
        deliveredAmount += result.undelivered();
      }
      if (deliveredAmount > 0) {
        ItemStack delivered = copy.clone();
        delivered.setAmount(deliveredAmount);
        issueLogger.accept(player, delivered);
      }
      return;
    }
    if ((click == ClickType.LEFT || click == ClickType.RIGHT)
        && isEmpty(event.getView().getCursor())) {
      event.getView().setCursor(copy);
      issueLogger.accept(player, copy);
    }
  }

  public void handleDrag(InventoryDragEvent event) {
    int topSize = event.getView().getTopInventory().getSize();
    for (int rawSlot : event.getRawSlots()) {
      if (rawSlot >= 0 && rawSlot < topSize) {
        event.setCancelled(true);
        return;
      }
    }
  }

  static List<String> catalogIds(StorageTierCatalog storageTiers) {
    return catalogIds(FeatureAccessConfig.defaults(), storageTiers);
  }

  static List<String> catalogIds(
      FeatureAccessConfig featureAccess, StorageTierCatalog storageTiers) {
    FeatureAccessConfig access =
        featureAccess == null ? FeatureAccessConfig.defaults() : featureAccess;
    List<String> ids = new ArrayList<>();
    for (StorageTier tier : storageTiers.tiers()) {
      ids.add("storage:" + tier.key().toLowerCase(Locale.ROOT));
    }
    for (String id : FIXED_ITEM_IDS) {
      if (FixedItemCatalog.WIRELESS_BOOSTER.id().equals(id)) {
        if (access.isCatalogVisible(id)) {
          for (WirelessBoosterTier tier : WirelessBoosterTier.values()) {
            ids.add(id + ":" + tier.id());
          }
        }
        continue;
      }
      if (access.isCatalogVisible(id)) {
        ids.add(id);
      }
    }
    validateCatalogSize(ids.size());
    return List.copyOf(ids);
  }

  static Component title(String localizedTitle) {
    return Component.text(
        localizedTitle == null || localizedTitle.isBlank() ? TITLE : localizedTitle);
  }

  static void validateCatalogSize(int size) {
    if (size > SIZE) {
      throw new IllegalStateException(
          "Exort give menu has " + size + " items, but only " + SIZE + " slots are available.");
    }
  }

  static int cursorDestroyAmount(int currentAmount, ClickType click) {
    if (currentAmount <= 0) {
      return 0;
    }
    return switch (click) {
      case LEFT -> currentAmount;
      case RIGHT -> 1;
      default -> 0;
    };
  }

  static boolean canDestroyCustomItem(boolean customItem, boolean hasStorageId) {
    return customItem && !hasStorageId;
  }

  static boolean canMutate(Player player) {
    return player != null
        && (player.hasPermission(PERMISSION_GIVE) || player.hasPermission(PERMISSION_ADMIN));
  }

  private static List<ItemStack> catalogItems(
      CustomItems customItems,
      Supplier<ItemStack> wirelessTerminalFactory,
      FeatureAccessConfig featureAccess,
      StorageTierCatalog storageTiers) {
    FeatureAccessConfig access =
        featureAccess == null ? FeatureAccessConfig.defaults() : featureAccess;
    List<ItemStack> items = new ArrayList<>();
    for (StorageTier tier : storageTiers.tiers()) {
      items.add(oneItemCopy(customItems.storageItem(tier, null)));
    }
    items.add(oneItemCopy(customItems.storageCoreItem()));
    items.add(oneItemCopy(customItems.terminalItem()));
    items.add(oneItemCopy(customItems.craftingTerminalItem()));
    items.add(oneItemCopy(customItems.monitorItem()));
    items.add(oneItemCopy(customItems.importBusItem()));
    items.add(oneItemCopy(customItems.exportBusItem()));
    items.add(oneItemCopy(customItems.wireItem()));
    if (access.isCatalogVisible("relay")) {
      items.add(oneItemCopy(customItems.relayItem()));
    }
    if (access.isCatalogVisible("transmitter")) {
      items.add(oneItemCopy(customItems.transmitterItem()));
    }
    if (access.isCatalogVisible("chunk_loader")) {
      for (ChunkLoaderType type : ChunkLoaderType.all()) {
        items.add(oneItemCopy(customItems.chunkLoaderItem(type)));
      }
    }
    if (access.isCatalogVisible("wireless_terminal")) {
      items.add(oneItemCopy(wirelessTerminalFactory.get()));
    }
    if (access.isCatalogVisible("wireless_booster")) {
      for (WirelessBoosterTier tier : WirelessBoosterTier.values()) {
        items.add(oneItemCopy(customItems.wirelessBoosterItem(tier)));
      }
    }
    return List.copyOf(items);
  }

  private static ItemStack oneItemCopy(ItemStack item) {
    ItemStack copy = item.clone();
    copy.setAmount(1);
    return copy;
  }

  private void destroyCursorItem(InventoryClickEvent event, ItemStack cursor, ClickType click) {
    if (!canDestroy(cursor)) {
      return;
    }
    int remove = cursorDestroyAmount(cursor.getAmount(), click);
    if (remove <= 0) {
      return;
    }
    int remaining = cursor.getAmount() - remove;
    logDestroyed(event, cursor, remove);
    if (remaining <= 0) {
      event.getView().setCursor(null);
      return;
    }
    ItemStack updated = cursor.clone();
    updated.setAmount(remaining);
    event.getView().setCursor(updated);
  }

  private void destroyShiftClickedItem(InventoryClickEvent event) {
    if (!event.isShiftClick()) {
      return;
    }
    ItemStack current = event.getCurrentItem();
    if (canDestroy(current)) {
      logDestroyed(event, current, current.getAmount());
      event.setCurrentItem(null);
    }
  }

  private boolean canDestroy(ItemStack item) {
    CustomItems items = currentCustomItems();
    boolean customItem = items.isCustomItem(item);
    boolean hasPersistentId =
        customItem && (items.storageId(item).isPresent() || items.chunkLoaderId(item).isPresent());
    return canDestroyCustomItem(customItem, hasPersistentId);
  }

  private CustomItems currentCustomItems() {
    return Objects.requireNonNull(customItems.get(), "customItems");
  }

  private FeatureAccessConfig currentFeatureAccess() {
    FeatureAccessConfig access = featureAccess.get();
    return access == null ? FeatureAccessConfig.defaults() : access;
  }

  private StorageTierCatalog currentStorageTierCatalog() {
    return Objects.requireNonNull(storageTierCatalog.get(), "storageTierCatalog");
  }

  private ItemStack copyForDelivery(ItemStack sample) {
    CustomItems items = currentCustomItems();
    if (items.isChunkLoader(sample)) {
      return items.chunkLoaderItem(items.chunkLoaderType(sample));
    }
    return oneItemCopy(sample);
  }

  private void logDestroyed(InventoryClickEvent event, ItemStack stack, int amount) {
    if (!(event.getWhoClicked() instanceof Player player) || isEmpty(stack) || amount <= 0) {
      return;
    }
    ItemStack copy = stack.clone();
    copy.setAmount(amount);
    destroyLogger.accept(player, copy);
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getAmount() <= 0 || stack.getType() == Material.AIR;
  }
}
