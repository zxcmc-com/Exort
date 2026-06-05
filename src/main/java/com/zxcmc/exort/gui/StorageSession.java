package com.zxcmc.exort.gui;

import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class StorageSession extends AbstractStorageSession {
  private final boolean useFillers;
  private final Map<Integer, DisplayEntry> slotEntries = new HashMap<>();
  private long infoErrorUntilMs;
  private int infoErrorTaskId = -1;
  private static final long INFO_ERROR_TICKS = 20L * 5;
  private final InfoButtonState infoButtonState;

  public StorageSession(
      Player viewer,
      StorageCache cache,
      StorageTier tier,
      Lang lang,
      ItemNameService itemNames,
      Block terminalBlock,
      Location storageLocation,
      SessionManager manager,
      boolean readOnly,
      Component title,
      boolean useFillers,
      long confirmTimeoutMs,
      SortMode sortMode,
      boolean wireless) {
    super(
        viewer,
        cache,
        tier,
        lang,
        itemNames,
        manager,
        terminalBlock,
        storageLocation,
        readOnly,
        title,
        GuiLayout.INVENTORY_SIZE,
        sortMode,
        wireless);
    this.useFillers = useFillers;
    this.infoButtonState = new InfoButtonState(confirmTimeoutMs);
  }

  public void onClose() {
    sortFrozen = false;
    sortOrder.clear();
    bossBar.removeAll();
    cancelWirelessRefreshTask();
    if (infoErrorTaskId != -1) {
      Bukkit.getScheduler().cancelTask(infoErrorTaskId);
      infoErrorTaskId = -1;
    }
    infoButtonState.resetConfirm();
  }

  @Override
  public SessionType type() {
    return SessionType.STORAGE;
  }

  public void render() {
    displayList = buildDisplayList();
    GuiPageWindow pageWindow = GuiPageWindow.forSlots(page, displayList.size(), pageSize());
    if (pageWindow.page() != page) {
      page = pageWindow.page();
      displayList = buildDisplayList();
      pageWindow = GuiPageWindow.forSlots(page, displayList.size(), pageSize());
    } else {
      page = pageWindow.page();
    }
    slotEntries.clear();
    ItemStack[] contents = new ItemStack[GuiLayout.INVENTORY_SIZE];
    boolean fillSearchPad = useFillers && isSearchResultsPage();
    for (int i = 0; i < pageWindow.pageSize(); i++) {
      int idx = pageWindow.startIndex() + i;
      DisplayEntry entry = entryAt(idx);
      if (entry == null) {
        contents[i] = fillSearchPad ? GuiItems.filler(true) : null;
        continue;
      }
      ItemStack stack = entry.sample().clone();
      stack.setAmount(entry.amount());
      contents[i] = stack;
      slotEntries.put(i, entry);
    }

    // Buttons and fillers
    String pageInfo = tr("gui.page_info", pageWindow.displayPage(), pageWindow.totalPages());
    List<Component> pageLore = new ArrayList<>();
    pageLore.add(Component.text(pageInfo).decoration(TextDecoration.ITALIC, false));
    String searchLabel = currentPageSearchLabel();
    if (searchLabel != null) {
      pageLore.add(Component.text(searchLabel).decoration(TextDecoration.ITALIC, false));
    } else {
      String categoryLabel = currentPageCategoryLabel();
      if (categoryLabel != null) {
        pageLore.add(Component.text(categoryLabel).decoration(TextDecoration.ITALIC, false));
      }
    }
    if (isDisplayListTruncated()) {
      pageLore.add(
          Component.text(
                  tr("gui.list_truncated", StorageDisplayListBuilder.DEFAULT_MAX_DISPLAY_ENTRIES))
              .decoration(TextDecoration.ITALIC, false));
    }
    contents[GuiLayout.Storage.SLOT_PREV] =
        GuiItems.pagePrev(tr("gui.prev_page"), pageLore, useFillers);
    contents[GuiLayout.Storage.SLOT_NEXT] =
        GuiItems.pageNext(tr("gui.next_page"), pageLore, useFillers);
    if (useFillers) {
      ItemStack filler = GuiItems.filler(true);
      for (int i = 46; i <= 52; i++) {
        contents[i] = filler;
      }
    }
    contents[GuiLayout.Storage.SLOT_SORT] = sortButton();
    contents[GuiLayout.Storage.SLOT_INFO] = infoButton();
    contents[GuiLayout.Storage.SLOT_SEARCH] = searchButton();

    if (!useFillers) {
      ItemStack filler = GuiItems.filler(false);
      for (int i = 0; i < contents.length; i++) {
        if (contents[i] == null) {
          contents[i] = filler;
        }
      }
    }

    inventory.setContents(contents);
    updateBossBar();
    handleWirelessRefreshAfterRender();
  }

  private ItemStack sortButton() {
    return StorageGuiControls.sortButton(lang, viewer, sortMode, useFillers);
  }

  private ItemStack searchButton() {
    return StorageGuiControls.searchButton(lang, viewer, hasSearch(), getSearchQuery(), useFillers);
  }

  private ItemStack infoButton() {
    return StorageGuiControls.infoButton(
        lang,
        viewer,
        cache,
        tier,
        infoButtonState.showStorageId(),
        readOnly,
        isInfoErrorActive(),
        infoErrorMessage,
        infoButtonState.confirmRemaining(),
        infoButtonState.isBlocked(),
        useFillers);
  }

  private boolean isInfoErrorActive() {
    return System.currentTimeMillis() < infoErrorUntilMs;
  }

  @Override
  protected void triggerInfoError() {
    infoErrorUntilMs = System.currentTimeMillis() + INFO_ERROR_TICKS * 50L;
    if (infoErrorTaskId != -1) {
      Bukkit.getScheduler().cancelTask(infoErrorTaskId);
    }
    infoErrorTaskId =
        Bukkit.getScheduler()
            .runTaskLater(
                manager.plugin(),
                () -> {
                  infoErrorTaskId = -1;
                  infoErrorMessage = null;
                  if (!viewer.isOnline()) return;
                  render();
                },
                INFO_ERROR_TICKS + 1)
            .getTaskId();
    render();
  }

  private void handleInfoClick(InventoryClickEvent event) {
    if (!event.isShiftClick() || !event.isRightClick()) {
      infoButtonState.resetConfirm();
      return;
    }
    if (!infoButtonState.showStorageId()) {
      infoButtonState.revealStorageId();
      render();
      return;
    }
    if (!readOnly) {
      infoButtonState.resetConfirm();
      return;
    }
    if (!infoButtonState.isConfirming()) {
      infoButtonState.startConfirm();
      render();
      return;
    }
    int remaining = infoButtonState.decrementConfirm();
    if (remaining > 0) {
      render();
      return;
    }
    if (manager.isModeratorLocked(cache.getStorageId(), viewer.getUniqueId())) {
      infoButtonState.markBlocked();
      render();
      return;
    }
    if (!manager.forceWriterFromInfo(this)) {
      infoButtonState.markBlocked();
      render();
    }
  }

  public void handleClick(InventoryClickEvent event) {
    if (event.getClickedInventory() == null) return;
    if (event.getInventory().getHolder() != this) return;

    int rawSlot = event.getRawSlot();
    if (rawSlot >= inventory.getSize()) {
      handleBottomInventoryShiftDeposit(event);
      return;
    }

    event.setCancelled(true);
    if (rawSlot >= GuiLayout.PAGE_SIZE) {
      if (rawSlot == GuiLayout.Storage.SLOT_SEARCH && event.isShiftClick()) {
        clearSearch();
        render();
        return;
      }
      if (rawSlot == GuiLayout.Storage.SLOT_INFO) {
        handleInfoClick(event);
        return;
      }
      handleButtonClick(rawSlot);
      return;
    }

    handleStorageSlotTransfer(event, slotEntries.get(rawSlot), SortEvent.NONE, SortEvent.WITHDRAW);
  }

  private void handleButtonClick(int rawSlot) {
    if (rawSlot == GuiLayout.Storage.SLOT_PREV && page > 0) {
      page--;
      render();
    } else if (rawSlot == GuiLayout.Storage.SLOT_NEXT) {
      GuiPageWindow pageWindow = GuiPageWindow.forSlots(page, displayList.size(), pageSize());
      if (pageWindow.hasNext()) {
        page = pageWindow.page() + 1;
        render();
      }
    } else if (rawSlot == GuiLayout.Storage.SLOT_INFO) {
      // info button: no action
    } else if (rawSlot == GuiLayout.Storage.SLOT_SEARCH) {
      manager.openSearch(viewer, this);
    } else if (rawSlot == GuiLayout.Storage.SLOT_SORT) {
      if (readOnly) return;
      sortMode =
          switch (sortMode) {
            case AMOUNT -> SortMode.NAME;
            case NAME -> SortMode.ID;
            case ID -> SortMode.CATEGORY;
            case CATEGORY -> SortMode.AMOUNT;
          };
      sortFrozen = false;
      sortOrder.clear();
      manager.updateSortMode(cache, sortMode);
    }
  }

  @Override
  public void onSortEvent(SortEvent event) {
    if (event == SortEvent.WITHDRAW) {
      sortFrozen = true;
    } else if (event == SortEvent.DEPOSIT) {
      sortFrozen = false;
      sortOrder.clear();
    }
  }
}
