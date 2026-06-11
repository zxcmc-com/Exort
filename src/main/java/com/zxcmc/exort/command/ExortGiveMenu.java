package com.zxcmc.exort.command;

import com.zxcmc.exort.items.CustomItemRegistry;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.storage.StorageTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

  private static final List<String> FIXED_ITEM_IDS = CustomItemRegistry.fixedItemIds();

  private final Inventory inventory;
  private final CustomItems customItems;

  public ExortGiveMenu(
      CustomItems customItems, Supplier<ItemStack> wirelessTerminalFactory, Component title) {
    this.customItems = Objects.requireNonNull(customItems, "customItems");
    List<ItemStack> items =
        catalogItems(
            customItems,
            Objects.requireNonNull(wirelessTerminalFactory, "wirelessTerminalFactory"));
    validateCatalogSize(items.size());
    inventory = Bukkit.createInventory(this, SIZE, title == null ? Component.text(TITLE) : title);
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
    if (!topSlot) {
      destroyShiftClickedItem(event);
      return;
    }
    if (!(event.getWhoClicked() instanceof Player player)) {
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
    ItemStack copy = oneItemCopy(sample);
    if (event.isShiftClick()) {
      player.getInventory().addItem(copy);
      return;
    }
    if ((click == ClickType.LEFT || click == ClickType.RIGHT)
        && isEmpty(event.getView().getCursor())) {
      event.getView().setCursor(copy);
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

  static List<String> catalogIds() {
    List<String> ids = new ArrayList<>();
    for (StorageTier tier : StorageTier.allTiers()) {
      ids.add("storage:" + tier.key().toLowerCase(Locale.ROOT));
    }
    ids.addAll(FIXED_ITEM_IDS);
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

  private static List<ItemStack> catalogItems(
      CustomItems customItems, Supplier<ItemStack> wirelessTerminalFactory) {
    List<ItemStack> items = new ArrayList<>();
    for (StorageTier tier : StorageTier.allTiers()) {
      items.add(oneItemCopy(customItems.storageItem(tier, null)));
    }
    items.add(oneItemCopy(customItems.storageCoreItem()));
    items.add(oneItemCopy(customItems.terminalItem()));
    items.add(oneItemCopy(customItems.craftingTerminalItem()));
    items.add(oneItemCopy(customItems.monitorItem()));
    items.add(oneItemCopy(customItems.importBusItem()));
    items.add(oneItemCopy(customItems.exportBusItem()));
    items.add(oneItemCopy(customItems.wireItem()));
    items.add(oneItemCopy(wirelessTerminalFactory.get()));
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
      event.setCurrentItem(null);
    }
  }

  private boolean canDestroy(ItemStack item) {
    boolean customItem = customItems.isCustomItem(item);
    boolean hasStorageId = customItem && customItems.storageId(item).isPresent();
    return canDestroyCustomItem(customItem, hasStorageId);
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getAmount() <= 0 || stack.getType() == Material.AIR;
  }
}
