package com.zxcmc.exort.items.listener;

import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.items.StorageItemNameEditor;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageDisplayName;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

public final class StorageAnvilRenameListener implements Listener {
  private static final int ANVIL_INPUT_SLOT = 0;
  private static final int ANVIL_RESULT_SLOT = 2;

  private final Plugin plugin;
  private final StorageKeys keys;
  private final CustomItems customItems;

  public StorageAnvilRenameListener(Plugin plugin, StorageKeys keys, CustomItems customItems) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.keys = Objects.requireNonNull(keys, "keys");
    this.customItems = Objects.requireNonNull(customItems, "customItems");
  }

  @EventHandler
  public void onPrepareAnvil(PrepareAnvilEvent event) {
    showRawNameInAnvilInput(keys, event.getInventory().getItem(ANVIL_INPUT_SLOT));
    ItemStack result =
        prepareResult(
            keys,
            customItems,
            event.getInventory().getItem(ANVIL_INPUT_SLOT),
            event.getInventory().getItem(1),
            event.getView().getRenameText());
    if (result == null) {
      return;
    }
    event.setResult(result);
    AnvilView view = event.getView();
    if (view.getRepairCost() < 1) {
      view.setRepairCost(1);
    }
    view.setRepairItemCountCost(0);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onAnvilClickBefore(InventoryClickEvent event) {
    if (event.getView().getType() != InventoryType.ANVIL) {
      return;
    }
    if (event.getRawSlot() == ANVIL_INPUT_SLOT) {
      showRawNameInAnvilInput(keys, event.getCursor());
      return;
    }
    if (event.getClickedInventory() == event.getView().getBottomInventory()) {
      showRawNameInAnvilInput(keys, event.getCurrentItem());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onAnvilResultClick(InventoryClickEvent event) {
    if (event.getView().getType() != InventoryType.ANVIL
        || event.getRawSlot() != ANVIL_RESULT_SLOT) {
      return;
    }
    ItemStack result =
        prepareResult(
            keys,
            customItems,
            event.getView().getTopInventory().getItem(ANVIL_INPUT_SLOT),
            event.getView().getTopInventory().getItem(1),
            ((AnvilView) event.getView()).getRenameText());
    if (result != null) {
      event.setCurrentItem(result);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onAnvilClickAfter(InventoryClickEvent event) {
    if (event.getView().getType() != InventoryType.ANVIL) {
      return;
    }
    HumanEntity viewer = event.getWhoClicked();
    Bukkit.getScheduler().runTask(plugin, () -> restoreVisibleItemsOutsideInput(viewer));
  }

  @EventHandler
  public void onAnvilClose(InventoryCloseEvent event) {
    if (event.getView().getType() != InventoryType.ANVIL) {
      return;
    }
    Inventory top = event.getView().getTopInventory();
    for (int slot = 0; slot < top.getSize(); slot++) {
      restoreStorageItemAppearance(top, slot);
    }
    restoreVisibleItemsOutsideInput(event.getPlayer());
    HumanEntity player = event.getPlayer();
    Bukkit.getScheduler().runTask(plugin, () -> restoreVisibleItemsOutsideInput(player));
  }

  public static ItemStack prepareResult(
      StorageKeys keys,
      CustomItems customItems,
      ItemStack first,
      ItemStack second,
      String renameText) {
    if (keys == null || !isEmpty(second) || renameText == null) {
      return null;
    }
    if (first == null || first.getType() == Material.AIR || first.getAmount() <= 0) {
      return null;
    }
    ItemStack result = first.clone();
    boolean changed =
        customItems != null
            ? customItems.setStorageDisplayName(result, renameText)
            : StorageItemNameEditor.apply(keys, result, renameText);
    if (!changed) {
      return null;
    }
    result.setAmount(1);
    return result;
  }

  public static boolean showRawNameInAnvilInput(StorageKeys keys, ItemStack stack) {
    String displayName = StorageItemNameEditor.displayName(keys, stack).orElse(null);
    if (displayName == null) {
      return false;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return false;
    }
    meta.customName(StorageDisplayName.anvilInputComponent(displayName));
    stack.setItemMeta(meta);
    return true;
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
  }

  private void restoreVisibleItemsOutsideInput(HumanEntity viewer) {
    if (viewer == null) {
      return;
    }
    restoreStorageItemAppearance(viewer.getInventory());
    InventoryView view = viewer.getOpenInventory();
    ItemStack cursor = view.getCursor();
    if (restoreStorageItemAppearance(cursor)) {
      view.setCursor(cursor);
    }
    if (view.getType() == InventoryType.ANVIL) {
      restoreStorageItemAppearance(view.getTopInventory(), ANVIL_RESULT_SLOT);
    }
  }

  private void restoreStorageItemAppearance(Inventory inventory) {
    if (inventory == null) {
      return;
    }
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      restoreStorageItemAppearance(inventory, slot);
    }
  }

  private void restoreStorageItemAppearance(Inventory inventory, int slot) {
    if (inventory == null || slot < 0 || slot >= inventory.getSize()) {
      return;
    }
    ItemStack stack = inventory.getItem(slot);
    if (!restoreStorageItemAppearance(stack)) {
      return;
    }
    inventory.setItem(slot, stack);
  }

  private boolean restoreStorageItemAppearance(ItemStack stack) {
    return StorageItemNameEditor.isStorageItem(keys, stack)
        && customItems.refreshItem(stack, null, false);
  }
}
