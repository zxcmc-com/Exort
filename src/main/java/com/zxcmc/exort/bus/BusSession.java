package com.zxcmc.exort.bus;

import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.items.ItemKeyUtil;
import com.zxcmc.exort.core.ui.BossBarManager;
import com.zxcmc.exort.gui.GuiItems;
import com.zxcmc.exort.gui.GuiLayout;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class BusSession implements InventoryHolder {
  private static final int[] FILTER_SLOTS = GuiLayout.Bus.FILTER_SLOTS;

  private final Player viewer;
  private final BusSessionManager manager;
  private final BusState state;
  private final Lang lang;
  private final BossBarManager bossBarManager;
  private final Inventory inventory;
  private final boolean useFillers;
  private final Block busBlock;
  private boolean showStorageId;

  public BusSession(
      Player viewer,
      BusSessionManager manager,
      BusState state,
      Lang lang,
      BossBarManager bossBarManager,
      boolean useFillers,
      Component title,
      Block busBlock) {
    this.viewer = viewer;
    this.manager = manager;
    this.state = state;
    this.lang = lang;
    this.bossBarManager = bossBarManager;
    this.useFillers = useFillers;
    this.busBlock = busBlock;
    this.inventory = Bukkit.createInventory(this, GuiLayout.Bus.SIZE, title);
  }

  public Player getViewer() {
    return viewer;
  }

  public BusState getState() {
    return state;
  }

  public Block getBusBlock() {
    return busBlock;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  public void render() {
    ItemStack[] contents = new ItemStack[GuiLayout.Bus.SIZE];
    ItemStack[] filters = state.filters();
    for (int i = 0; i < FILTER_SLOTS.length; i++) {
      ItemStack stack = i < filters.length ? filters[i] : null;
      if (stack != null && stack.getType() != Material.AIR) {
        ItemStack ghost = stack.clone();
        ghost.setAmount(1);
        contents[FILTER_SLOTS[i]] = ghost;
      }
    }

    contents[GuiLayout.Bus.SLOT_MODE] = modeButton();
    contents[GuiLayout.Bus.SLOT_INFO] = infoButton();

    ItemStack filler = GuiItems.filler(useFillers);
    for (int i = 0; i < GuiLayout.Bus.SIZE; i++) {
      if (contents[i] != null) continue;
      if (isFilterSlot(i)) continue;
      contents[i] = filler;
    }

    inventory.setContents(contents);
    updateStatusBar();
  }

  public void handleClick(InventoryClickEvent event) {
    if (event.getClickedInventory() == null) return;
    if (event.getInventory().getHolder() != this) return;

    int rawSlot = event.getRawSlot();
    int topSize = inventory.getSize();
    if (rawSlot >= topSize) {
      if (event.isShiftClick()) {
        event.setCancelled(true);
      }
      return;
    }
    event.setCancelled(true);
    int filterIndex = filterIndex(rawSlot);
    if (filterIndex >= 0) {
      handleFilterSlotClick(event, filterIndex);
      return;
    }
    if (rawSlot == GuiLayout.Bus.SLOT_MODE) {
      state.setMode(state.mode().next());
      render();
      return;
    }
    if (rawSlot == GuiLayout.Bus.SLOT_INFO) {
      if (event.isShiftClick() && event.isRightClick()) {
        showStorageId = true;
        render();
      }
    }
  }

  public void onClose() {
    manager.saveSettings(state);
  }

  private void handleFilterSlotClick(InventoryClickEvent event, int index) {
    ItemStack cursor = event.getView().getCursor();
    if (cursor == null || cursor.getType() == Material.AIR) {
      state.setFilter(index, null);
      render();
      return;
    }
    ItemStack sample = ItemKeyUtil.sampleItem(cursor);
    state.setFilter(index, sample);
    render();
  }

  private ItemStack modeButton() {
    String modeKey =
        switch (state.mode()) {
          case DISABLED -> "gui.bus.mode.disabled";
          case WHITELIST -> "gui.bus.mode.whitelist";
          case BLACKLIST -> "gui.bus.mode.blacklist";
          case ALL -> "gui.bus.mode.all";
        };
    Component title =
        Component.text(lang.tr("gui.bus.mode.title") + ": " + lang.tr(modeKey))
            .decoration(TextDecoration.ITALIC, false);
    List<Component> lore =
        List.of(
            Component.text(lang.tr("gui.bus.mode.hint")).decoration(TextDecoration.ITALIC, false));
    return GuiItems.button(Material.COMPARATOR, title, lore, useFillers);
  }

  private ItemStack infoButton() {
    List<Component> lore = new ArrayList<>();
    BusSessionManager.BusLinkStatus status = manager.resolveStatus(state);
    boolean loopDisabled = status.loopDisabled() && state.mode() != BusMode.DISABLED;
    if (status.loopDisabled()) {
      lore.add(
          Component.text(lang.tr("message.bus.loop_detected"))
              .color(NamedTextColor.RED)
              .decoration(TextDecoration.ITALIC, false));
    }
    if (status.storageState() == BusSessionManager.StorageState.OK) {
      lore.add(
          Component.text(lang.tr("gui.bus.info.storage", status.storageName()))
              .decoration(TextDecoration.ITALIC, false));
      if (showStorageId && status.storageId() != null && !status.storageId().isBlank()) {
        lore.add(
            Component.text(lang.tr("gui.bus.info.storage_id", status.storageId()))
                .decoration(TextDecoration.ITALIC, false));
      }
    } else if (status.storageState() == BusSessionManager.StorageState.MULTIPLE) {
      lore.add(
          Component.text(lang.tr("gui.bus.info.storage_multiple"))
              .color(NamedTextColor.RED)
              .decoration(TextDecoration.ITALIC, false));
    } else {
      lore.add(
          Component.text(lang.tr("gui.bus.info.storage_missing"))
              .color(NamedTextColor.RED)
              .decoration(TextDecoration.ITALIC, false));
    }

    if (status.storageState() == BusSessionManager.StorageState.OK) {
      if (status.inventoryName() != null) {
        lore.add(
            Component.text(lang.tr("gui.bus.info.vanilla", status.inventoryName()))
                .decoration(TextDecoration.ITALIC, false));
      } else {
        lore.add(
            Component.text(lang.tr("gui.bus.info.vanilla_missing"))
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
      }
    }
    Component title =
        Component.text(lang.tr("gui.bus.info.title")).decoration(TextDecoration.ITALIC, false);
    if (loopDisabled) {
      return GuiItems.infoErrorButton(title, lore);
    }
    return GuiItems.infoButton(title, lore, useFillers);
  }

  private boolean isFilterSlot(int slot) {
    return filterIndex(slot) >= 0;
  }

  private int filterIndex(int slot) {
    for (int i = 0; i < FILTER_SLOTS.length; i++) {
      if (FILTER_SLOTS[i] == slot) {
        return i;
      }
    }
    return -1;
  }

  private void updateStatusBar() {
    BusSessionManager.BusLinkStatus status = manager.resolveStatus(state);
    if (state.mode() == BusMode.DISABLED) {
      bossBarManager.remove(viewer);
      return;
    }
    if (status.storageState() != BusSessionManager.StorageState.OK) {
      String errorKey =
          status.storageState() == BusSessionManager.StorageState.MULTIPLE
              ? "message.bus.multiple_storages"
              : "message.bus.no_storage";
      bossBarManager.showError(viewer, lang.tr(errorKey), 60L);
      return;
    }
    if (status.inventoryName() == null) {
      bossBarManager.showError(viewer, lang.tr("message.bus.no_inventory"), 60L);
      return;
    }
    if (status.loopDisabled()) {
      bossBarManager.showError(viewer, lang.tr("message.bus.loop_detected"), 60L);
      return;
    }
    bossBarManager.remove(viewer);
  }
}
