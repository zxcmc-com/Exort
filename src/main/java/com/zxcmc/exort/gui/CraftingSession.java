package com.zxcmc.exort.gui;

import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemCraftResult;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;

public class CraftingSession extends AbstractStorageSession {
  private static final int[] STORAGE_SLOTS =
      new int[] {
        0, 1, 2, 3, 4,
        9, 10, 11, 12, 13,
        18, 19, 20, 21, 22,
        27, 28, 29, 30, 31,
        36, 37, 38, 39, 40
      };
  private static final int[] CRAFT_SLOTS = new int[] {6, 7, 8, 15, 16, 17, 24, 25, 26};
  private static final int[] CRAFT_INDEX = new int[54];
  private static final boolean[] STORAGE_SLOT = new boolean[54];

  private static final long INFO_ERROR_TICKS = 20L * 5;

  static {
    Arrays.fill(CRAFT_INDEX, -1);
    for (int i = 0; i < CRAFT_SLOTS.length; i++) {
      CRAFT_INDEX[CRAFT_SLOTS[i]] = i;
    }
    for (int slot : STORAGE_SLOTS) {
      STORAGE_SLOT[slot] = true;
    }
  }

  private final CraftingState state;
  private final CraftingRules craftingRules;
  private final boolean useFillers;
  private final long confirmTimeoutMs;
  private final InfoButtonState infoButtonState;
  private final Map<Integer, DisplayEntry> slotEntries = new HashMap<>();
  private long infoErrorUntilMs;
  private int infoErrorTaskId = -1;

  public CraftingSession(
      Player viewer,
      StorageCache cache,
      StorageTier tier,
      Lang lang,
      ItemNameService itemNames,
      Block terminalBlock,
      Location storageLocation,
      SessionManager manager,
      boolean readOnly,
      CraftingState state,
      CraftingRules craftingRules,
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
    this.state = state;
    this.craftingRules = craftingRules;
    this.useFillers = useFillers;
    this.confirmTimeoutMs = Math.max(0L, confirmTimeoutMs);
    this.infoButtonState = new InfoButtonState(confirmTimeoutMs);
  }

  @Override
  public SessionType type() {
    return SessionType.CRAFTING;
  }

  @Override
  protected int pageSize() {
    return STORAGE_SLOTS.length;
  }

  @Override
  public void onClose() {
    if (!flushBufferToStorage()) {
      flushBufferToPlayerOrDrop();
    }
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

    for (int i = 0; i < STORAGE_SLOTS.length; i++) {
      int idx = pageWindow.startIndex() + i;
      int slot = STORAGE_SLOTS[i];
      DisplayEntry entry = entryAt(idx);
      if (entry == null) {
        contents[slot] = fillSearchPad ? GuiItems.filler(true) : null;
        continue;
      }
      ItemStack stack = entry.sample().clone();
      stack.setAmount(entry.amount());
      contents[slot] = stack;
      slotEntries.put(slot, entry);
    }

    ItemStack[] grid = state.snapshot();
    for (int i = 0; i < CRAFT_SLOTS.length; i++) {
      ItemStack item = grid[i];
      if (item != null) {
        ItemStack ghost = item.clone();
        ghost.setAmount(1);
        contents[CRAFT_SLOTS[i]] = ghost;
      } else {
        contents[CRAFT_SLOTS[i]] = null;
      }
    }

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
    contents[GuiLayout.Crafting.SLOT_PREV] =
        GuiItems.pagePrev(tr("gui.prev_page"), pageLore, useFillers);
    contents[GuiLayout.Crafting.SLOT_NEXT] =
        GuiItems.pageNext(tr("gui.next_page"), pageLore, useFillers);
    contents[GuiLayout.Crafting.SLOT_STORAGE_CRAFT] = storageCraftButton();
    contents[GuiLayout.Crafting.SLOT_PLAYER_CRAFT] = playerCraftButton();
    contents[GuiLayout.Crafting.SLOT_SORT] = sortButton();
    contents[GuiLayout.Crafting.SLOT_INFO] = infoButton();
    contents[GuiLayout.Crafting.SLOT_SEARCH] = searchButton();

    if (hasCraftItems(grid)) {
      contents[GuiLayout.Crafting.SLOT_CLEAR] = clearButton();
    } else {
      contents[GuiLayout.Crafting.SLOT_CLEAR] = useFillers ? GuiItems.filler(true) : null;
    }
    CraftPlan plan = computePlan();
    CraftingState.Buffer buffer = state.snapshotBuffer();
    if (buffer != null && (plan == null || !buffer.key().equals(plan.resultKey))) {
      flushBufferToStorage();
    }
    if (plan == null) {
      contents[GuiLayout.Crafting.SLOT_OUTPUT] = null;
    } else {
      int amount = state.bufferAmount(plan.resultKey);
      if (amount <= 0) {
        if (plan.maxCraft <= 0) {
          contents[GuiLayout.Crafting.SLOT_OUTPUT] = null;
        } else {
          amount = Math.max(1, plan.resultPerCraft);
          ItemStack out = plan.result.clone();
          out.setAmount(amount);
          contents[GuiLayout.Crafting.SLOT_OUTPUT] = out;
        }
      } else {
        ItemStack out = plan.result.clone();
        out.setAmount(amount);
        contents[GuiLayout.Crafting.SLOT_OUTPUT] = out;
      }
    }

    if (useFillers) {
      ItemStack filler = GuiItems.filler(true);
      for (int i = 0; i < contents.length; i++) {
        if (contents[i] != null) continue;
        if (isStorageSlot(i)) continue;
        if (craftIndex(i) >= 0) continue;
        if (i == GuiLayout.Crafting.SLOT_OUTPUT) continue;
        contents[i] = filler;
      }
    } else {
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

  public void handleClick(InventoryClickEvent event) {
    if (event.getClickedInventory() == null) return;
    if (event.getInventory().getHolder() != this) return;

    int rawSlot = event.getRawSlot();
    int topSize = inventory.getSize();

    if (rawSlot >= topSize) {
      handleBottomClick(event);
      return;
    }

    event.setCancelled(true);

    if (rawSlot == GuiLayout.Crafting.SLOT_PREV) {
      state.resetConfirm();
      if (page > 0) {
        page--;
        render();
      }
      return;
    }
    if (rawSlot == GuiLayout.Crafting.SLOT_NEXT) {
      state.resetConfirm();
      GuiPageWindow pageWindow = GuiPageWindow.forSlots(page, displayList.size(), pageSize());
      if (pageWindow.hasNext()) {
        page = pageWindow.page() + 1;
        render();
      }
      return;
    }
    if (rawSlot == GuiLayout.Crafting.SLOT_CLEAR) {
      if (readOnly) return;
      boolean hadBuffer = hasBufferedOutput();
      boolean flushed = flushBufferToStorage();
      if (hadBuffer && !flushed) {
        manager.renderStorage(cache.getStorageId(), SortEvent.NONE);
        return;
      }
      state.clear();
      manager.renderStorage(cache.getStorageId(), flushed ? SortEvent.DEPOSIT : SortEvent.NONE);
      return;
    }
    if (rawSlot == GuiLayout.Crafting.SLOT_OUTPUT) {
      if (readOnly) return;
      state.resetConfirm();
      handleOutputClick(event);
      return;
    }
    if (rawSlot == GuiLayout.Crafting.SLOT_SEARCH) {
      if (event.isShiftClick()) {
        clearSearch();
        render();
      } else {
        manager.openSearch(viewer, this);
      }
      return;
    }
    if (rawSlot == GuiLayout.Crafting.SLOT_STORAGE_CRAFT
        || rawSlot == GuiLayout.Crafting.SLOT_PLAYER_CRAFT) {
      if (readOnly) return;
      CraftingState.ConfirmTarget target =
          rawSlot == GuiLayout.Crafting.SLOT_STORAGE_CRAFT
              ? CraftingState.ConfirmTarget.STORAGE
              : CraftingState.ConfirmTarget.PLAYER;
      handleCraftButton(event, target);
      return;
    }
    if (rawSlot == GuiLayout.Crafting.SLOT_SORT) {
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
      return;
    }
    if (rawSlot == GuiLayout.Crafting.SLOT_INFO) {
      handleInfoClick(event);
      return;
    }

    int craftIndex = craftIndex(rawSlot);
    if (craftIndex >= 0) {
      if (readOnly) return;
      handleCraftSlotClick(event, craftIndex);
      return;
    }

    if (isStorageSlot(rawSlot)) {
      state.resetConfirm();
      handleStorageClick(event, rawSlot);
    }
  }

  private void handleBottomClick(InventoryClickEvent event) {
    state.resetConfirm();
    handleBottomInventoryShiftDeposit(event);
  }

  private void handleCraftSlotClick(InventoryClickEvent event, int index) {
    state.resetConfirm();
    boolean hadBuffer = hasBufferedOutput();
    boolean flushed = flushBufferToStorage();
    if (hadBuffer && !flushed) {
      manager.renderStorage(cache.getStorageId(), SortEvent.NONE);
      return;
    }
    ItemStack cursor = event.getView().getCursor();
    if (cursor == null || cursor.getType() == Material.AIR) {
      state.setSlot(index, null);
    } else {
      ItemStack sample = cursor.clone();
      sample.setAmount(1);
      state.setSlot(index, sample);
    }
    manager.renderStorage(cache.getStorageId(), flushed ? SortEvent.DEPOSIT : SortEvent.NONE);
  }

  private void handleStorageClick(InventoryClickEvent event, int rawSlot) {
    if (readOnly) {
      return;
    }
    state.resetConfirm();
    boolean hadBuffer = hasBufferedOutput();
    boolean flushed = flushBufferToStorage();
    if (hadBuffer && !flushed) {
      manager.renderStorage(cache.getStorageId(), SortEvent.NONE);
      return;
    }
    SortEvent noMoveEvent = flushed ? SortEvent.DEPOSIT : SortEvent.NONE;
    SortEvent withdrawEvent = flushed ? SortEvent.DEPOSIT : SortEvent.WITHDRAW;
    handleStorageSlotTransfer(event, slotEntries.get(rawSlot), noMoveEvent, withdrawEvent);
  }

  private void handleOutputClick(InventoryClickEvent event) {
    CraftPlan plan = computePlan();
    if (plan == null) {
      return;
    }
    if (plan.maxCraft <= 0 && state.bufferAmount(plan.resultKey) <= 0) {
      return;
    }
    state.resetConfirm();
    if (event.isShiftClick()) {
      boolean right = event.isRightClick();
      boolean changed = right ? craftStackToInventory(plan) : craftStackToCursor(plan);
      if (changed) {
        manager.renderStorage(cache.getStorageId(), SortEvent.WITHDRAW);
        clearCraftingGridIfNeeded(plan);
      }
      return;
    }
    boolean right = event.isRightClick();
    int giveAmount = right ? 1 : plan.resultPerCraft;
    if (craftToCursor(plan, giveAmount)) {
      manager.renderStorage(cache.getStorageId(), SortEvent.WITHDRAW);
      clearCraftingGridIfNeeded(plan);
    }
  }

  private List<StorageCache.ReservedItem> reserveIngredients(CraftPlan plan, int crafts) {
    WirelessTerminalService ws = manager.wirelessService();
    List<StorageCache.RemovalRequest> requests = new ArrayList<>(plan.ingredients.size());
    for (Ingredient ingredient : plan.ingredients.values()) {
      long remove = (long) ingredient.perCraft * crafts;
      requests.add(new StorageCache.RemovalRequest(ingredient.key, ingredient.sample, remove));
    }
    var reserved = cache.reserveAll(requests, ws);
    if (reserved.isPresent()) {
      pauseBusesForCraft();
      return reserved.get();
    }
    return null;
  }

  private void rollbackIngredients(List<StorageCache.ReservedItem> reserved) {
    if (reserved == null || reserved.isEmpty()) return;
    for (StorageCache.ReservedItem item : reserved) {
      cache.addItem(item.key(), item.sample(), item.amount());
    }
  }

  private void pauseBusesForCraft() {
    var busService = manager.busService();
    if (busService == null) return;
    busService.pauseForCraft(cache.getStorageId());
  }

  private void handleCraftButton(InventoryClickEvent event, CraftingState.ConfirmTarget target) {
    CraftPlan plan = computePlan();
    if (plan == null || plan.maxCraft <= 0) {
      state.resetConfirm();
      manager.renderStorage(cache.getStorageId(), SortEvent.NONE);
      return;
    }
    boolean shift = event.isShiftClick();
    boolean right = event.isRightClick();
    if (shift && right) {
      handleAllConfirm(plan, target);
      return;
    }
    state.resetConfirm();
    boolean hadBuffer = hasBufferedOutput();
    boolean flushed = flushBufferToStorage();
    if (hadBuffer && !flushed) {
      manager.renderStorage(cache.getStorageId(), SortEvent.NONE);
      return;
    }
    SortEvent eventType =
        flushed
            ? SortEvent.DEPOSIT
            : (target == CraftingState.ConfirmTarget.STORAGE
                ? SortEvent.DEPOSIT
                : SortEvent.WITHDRAW);
    if (shift) {
      int crafts = Math.min(plan.maxCraft, stackCrafts(plan, target));
      if (crafts <= 0) return;
      boolean crafted = craftToTarget(plan, target, crafts);
      manager.renderStorage(
          cache.getStorageId(),
          crafted ? eventType : (flushed ? SortEvent.DEPOSIT : SortEvent.NONE));
      if (crafted) {
        clearCraftingGridIfNeeded(plan);
      }
      return;
    }
    int crafts = Math.min(plan.maxCraft, 1);
    boolean crafted = craftToTarget(plan, target, crafts);
    manager.renderStorage(
        cache.getStorageId(), crafted ? eventType : (flushed ? SortEvent.DEPOSIT : SortEvent.NONE));
    if (crafted) {
      clearCraftingGridIfNeeded(plan);
    }
  }

  private void handleAllConfirm(CraftPlan plan, CraftingState.ConfirmTarget target) {
    boolean hadBuffer = hasBufferedOutput();
    boolean flushed = flushBufferToStorage();
    if (hadBuffer && !flushed) {
      manager.renderStorage(cache.getStorageId(), SortEvent.NONE);
      return;
    }
    SortEvent baseEvent =
        target == CraftingState.ConfirmTarget.STORAGE ? SortEvent.DEPOSIT : SortEvent.WITHDRAW;
    if (!state.isConfirming(target, confirmTimeoutMs)) {
      state.startConfirm(target, 4);
      manager.renderStorage(cache.getStorageId(), flushed ? SortEvent.DEPOSIT : SortEvent.NONE);
      return;
    }
    int remaining = state.decrementConfirm(target, confirmTimeoutMs);
    if (remaining > 0) {
      manager.renderStorage(cache.getStorageId(), flushed ? SortEvent.DEPOSIT : SortEvent.NONE);
      return;
    }
    int crafts = Math.min(plan.maxCraft, maxCraftsForTarget(plan, target));
    if (crafts > 0) {
      if (craftToTarget(plan, target, crafts)) {
        state.clear();
      }
    }
    state.resetConfirm();
    manager.renderStorage(cache.getStorageId(), flushed ? SortEvent.DEPOSIT : baseEvent);
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

  private int stackCrafts(CraftPlan plan, CraftingState.ConfirmTarget target) {
    int maxStack = plan.result.getMaxStackSize();
    int perCraft = plan.resultPerCraft;
    int maxCraftByStack = Math.max(1, maxStack / perCraft);
    int limit = maxCraftsForTarget(plan, target);
    return Math.min(maxCraftByStack, limit);
  }

  private int maxCraftsForTarget(CraftPlan plan, CraftingState.ConfirmTarget target) {
    return switch (target) {
      case STORAGE -> limitToStorage(plan, plan.maxCraft);
      case PLAYER -> limitToPlayer(plan, plan.maxCraft);
    };
  }

  private boolean craftToTarget(CraftPlan plan, CraftingState.ConfirmTarget target, int crafts) {
    if (crafts <= 0) return false;
    long outputAmount = outputAmount(plan, crafts);
    if (target == CraftingState.ConfirmTarget.STORAGE) {
      List<StorageCache.ReservedItem> reserved = reserveIngredients(plan, crafts);
      if (reserved == null) {
        showWirelessMissingError();
        return false;
      }
      List<OutputStack> output = outputAdditions(plan, outputAmount);
      if (!canStoreAdditions(output)) {
        rollbackIngredients(reserved);
        setInfoErrorMessage(null);
        triggerInfoError();
        return false;
      }
      addToStorage(output);
      deliverRemainders(plan, crafts);
      return true;
    }
    return craftToPlayer(plan, crafts);
  }

  private boolean craftToPlayer(CraftPlan plan, int crafts) {
    long outputAmount = outputAmount(plan, crafts);
    List<OutputStack> output = outputAdditions(plan, outputAmount);
    if (!canAddAllToPlayer(viewer, output)) {
      return false;
    }
    List<StorageCache.ReservedItem> reserved = reserveIngredients(plan, crafts);
    if (reserved == null) {
      showWirelessMissingError();
      return false;
    }
    if (!canAddAllToPlayer(viewer, output)) {
      rollbackIngredients(reserved);
      return false;
    }
    addAllToPlayerOrStorage(viewer, output);
    deliverRemainders(plan, crafts);
    return true;
  }

  private boolean craftStackToCursor(CraftPlan plan) {
    ItemStack cursor = viewer.getItemOnCursor();
    int fit;
    if (cursor == null || cursor.getType() == Material.AIR) {
      fit = plan.result.getMaxStackSize();
    } else {
      String cursorKey = ItemKeyUtil.keyFor(cursor);
      if (!cursorKey.equals(plan.resultKey)) return false;
      fit = cursor.getMaxStackSize() - cursor.getAmount();
    }
    if (fit <= 0) return false;
    StackCraft stack = planStackCraft(plan, fit);
    if (stack == null || stack.give <= 0) return false;
    List<StorageCache.ReservedItem> reserved = null;
    if (stack.crafts > 0) {
      reserved = reserveIngredients(plan, stack.crafts);
      if (reserved == null) {
        showWirelessMissingError();
        return false;
      }
      deliverRemainders(plan, stack.crafts);
    }
    if (cursor == null || cursor.getType() == Material.AIR) {
      ItemStack out = plan.result.clone();
      out.setAmount(stack.give);
      viewer.setItemOnCursor(out);
    } else {
      cursor.setAmount(cursor.getAmount() + stack.give);
      viewer.setItemOnCursor(cursor);
    }
    if (stack.bufferUsed > 0) {
      state.takeFromBuffer(plan.resultKey, stack.bufferUsed);
    }
    return true;
  }

  private boolean craftStackToInventory(CraftPlan plan) {
    int fit = maxAddableToPlayer(viewer, plan.result, plan.result.getMaxStackSize());
    if (fit <= 0) return false;
    StackCraft stack = planStackCraft(plan, fit);
    if (stack == null || stack.give <= 0) return false;
    List<OutputStack> output = outputAdditions(plan, stack.give);
    if (!canAddAllToPlayer(viewer, output)) return false;
    List<StorageCache.ReservedItem> reserved = null;
    if (stack.crafts > 0) {
      reserved = reserveIngredients(plan, stack.crafts);
      if (reserved == null) {
        showWirelessMissingError();
        return false;
      }
    }
    addAllToPlayerOrStorage(viewer, output);
    deliverRemainders(plan, stack.crafts);
    int bufferGive = Math.min(stack.bufferUsed, stack.give);
    if (bufferGive > 0) {
      state.takeFromBuffer(plan.resultKey, bufferGive);
    }
    int remaining = stack.give - bufferGive;
    int crafts = remaining / plan.resultPerCraft;
    int expectedCraftOutput = stack.crafts * plan.resultPerCraft;
    int craftedOutput = crafts * plan.resultPerCraft;
    if (craftedOutput < expectedCraftOutput) {
      cache.addItem(plan.resultKey, plan.result, expectedCraftOutput - craftedOutput);
    }
    return true;
  }

  private StackCraft planStackCraft(CraftPlan plan, int fit) {
    if (fit <= 0) return null;
    int maxStack = plan.result.getMaxStackSize();
    int buffer = state.bufferAmount(plan.resultKey);
    int bufferUsed = Math.min(buffer, maxStack);
    int craftsCap = 0;
    if (maxStack > bufferUsed && plan.maxCraft > 0) {
      craftsCap = Math.min(plan.maxCraft, (maxStack - bufferUsed) / plan.resultPerCraft);
    }
    int give = bufferUsed + craftsCap * plan.resultPerCraft;
    if (give <= 0) return null;
    if (fit < give) {
      int bufferGive = Math.min(bufferUsed, fit);
      int remainingFit = fit - bufferGive;
      int crafts = 0;
      if (remainingFit > 0 && plan.resultPerCraft > 0) {
        crafts = Math.min(craftsCap, remainingFit / plan.resultPerCraft);
      }
      give = bufferGive + crafts * plan.resultPerCraft;
      if (give <= 0) return null;
      return new StackCraft(give, bufferGive, crafts);
    }
    return new StackCraft(give, bufferUsed, craftsCap);
  }

  private boolean craftToCursor(CraftPlan plan, int giveAmount) {
    String resultKey = plan.resultKey;
    int buffered = state.bufferAmount(resultKey);
    boolean usingExistingBuffer = buffered > 0;
    ItemStack cursor = viewer.getItemOnCursor();
    int actualGive = giveAmount;
    if (buffered > 0) {
      actualGive = Math.min(giveAmount, buffered);
    }
    if (cursor != null && cursor.getType() != Material.AIR) {
      String cursorKey = ItemKeyUtil.keyFor(cursor);
      if (!cursorKey.equals(resultKey)) return false;
      int max = cursor.getMaxStackSize();
      if (cursor.getAmount() + actualGive > max) return false;
    }
    if (!usingExistingBuffer) {
      // consume ingredients for one craft and create buffer
      List<StorageCache.ReservedItem> reserved = reserveIngredients(plan, 1);
      if (reserved == null) {
        showWirelessMissingError();
        return false;
      }
      buffered = plan.resultPerCraft;
      actualGive = Math.min(giveAmount, buffered);
      int bufferLeft = buffered - actualGive;
      List<OutputStack> capacityAdditions = outputAdditions(plan, bufferLeft);
      if (!canStoreAdditions(capacityAdditions)) {
        rollbackIngredients(reserved);
        setInfoErrorMessage(null);
        triggerInfoError();
        return false;
      }
      deliverRemainders(plan, 1);
      if (bufferLeft > 0) {
        state.setBuffer(resultKey, plan.result, bufferLeft);
      } else {
        state.clearBuffer();
      }
    }
    if (cursor == null || cursor.getType() == Material.AIR) {
      ItemStack out = plan.result.clone();
      out.setAmount(actualGive);
      viewer.setItemOnCursor(out);
      if (usingExistingBuffer) {
        state.takeFromBuffer(resultKey, actualGive);
      }
      return true;
    }
    cursor.setAmount(cursor.getAmount() + actualGive);
    viewer.setItemOnCursor(cursor);
    if (usingExistingBuffer) {
      state.takeFromBuffer(resultKey, actualGive);
    }
    return true;
  }

  private void clearCraftingGridIfNeeded(CraftPlan plan) {
    if (plan.clearGrid()) {
      state.clear();
      render();
    }
  }

  private void showWirelessMissingError() {
    manager.playerFeedback().error(viewer, "message.wireless.missing_storage");
  }

  private int limitToPlayer(CraftPlan plan, int craftCount) {
    int low = 0;
    int high = Math.max(0, craftCount);
    while (low < high) {
      int mid = low + (high - low + 1) / 2;
      List<OutputStack> additions = outputAdditions(plan, outputAmount(plan, mid));
      if (canAddAllToPlayer(viewer, additions)) {
        low = mid;
      } else {
        high = mid - 1;
      }
    }
    return low;
  }

  private int limitToStorage(CraftPlan plan, int craftCount) {
    long space = spaceLeftFor(plan.result);
    int maxBySpace = (int) Math.min(Integer.MAX_VALUE, space / plan.resultPerCraft);
    return Math.min(craftCount, maxBySpace);
  }

  private long outputAmount(CraftPlan plan, int crafts) {
    return Math.max(0L, (long) crafts * plan.resultPerCraft);
  }

  private CraftPlan computePlan() {
    ItemStack[] grid = state.snapshot();
    boolean any = false;
    for (ItemStack stack : grid) {
      if (stack != null && stack.getType() != Material.AIR) {
        any = true;
        break;
      }
    }
    if (!any) return null;
    ItemStack[] matrix = new ItemStack[9];
    for (int i = 0; i < grid.length; i++) {
      if (grid[i] == null || grid[i].getType() == Material.AIR) {
        matrix[i] = null;
        continue;
      }
      ItemStack sample = grid[i].clone();
      sample.setAmount(1);
      matrix[i] = sample;
    }
    // Custom unbind recipe for wireless terminal inside crafting terminal.
    var ws = manager.wirelessService();
    if (ws != null) {
      int wirelessCount = 0;
      ItemStack wirelessItem = null;
      boolean hasOther = false;
      for (ItemStack stack : grid) {
        if (stack == null || stack.getType() == Material.AIR) continue;
        if (ws.isWireless(stack)) {
          wirelessCount++;
          wirelessItem = stack;
        } else {
          hasOther = true;
          break;
        }
      }
      if (!hasOther && wirelessCount == 1 && wirelessItem != null) {
        if (ws.currentCharge(wirelessItem) >= 100) { // allow unbind when fully charged
          if (!cache.hasMatchingWireless(ws, wirelessItem)) {
            return null;
          }
          ItemStack result = ws.resetLinkViaCraft(wirelessItem);
          ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(wirelessItem);
          Map<String, Ingredient> ingredients = new HashMap<>();
          ingredients.put(data.key(), new Ingredient(data.key(), data.sample(), 1));
          int perCraft = Math.max(1, result.getAmount());
          ItemStack resultSample = result.clone();
          resultSample.setAmount(1);
          return new CraftPlan(
              resultSample, perCraft, ingredients, Map.of(), 1, ItemKeyUtil.keyFor(result), true);
        }
      }
    }
    Recipe recipe = Bukkit.getCraftingRecipe(matrix, viewer.getWorld());
    if (recipe == null) return null;
    if (craftingRules != null && craftingRules.shouldBlock(matrix, recipe)) return null;
    ItemCraftResult craftResult =
        Bukkit.craftItemResult(copyMatrix(matrix), viewer.getWorld(), viewer);
    ItemStack result = craftResult.getResult();
    if (result == null || result.getType() == Material.AIR) return null;

    Map<String, Ingredient> ingredients = new HashMap<>();
    for (ItemStack stack : matrix) {
      if (stack == null || stack.getType() == Material.AIR) continue;
      ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(stack);
      String key = data.key();
      Ingredient existing = ingredients.get(key);
      if (existing == null) {
        ingredients.put(key, new Ingredient(key, data.sample(), 1));
      } else {
        ingredients.put(key, new Ingredient(key, existing.sample, existing.perCraft + 1));
      }
    }
    int maxCraft = Integer.MAX_VALUE;
    for (Ingredient ingredient : ingredients.values()) {
      long available = cache.getAmount(ingredient.key);
      int possible = (int) Math.min(Integer.MAX_VALUE, available / ingredient.perCraft);
      maxCraft = Math.min(maxCraft, possible);
    }
    String resultKey = ItemKeyUtil.keyFor(result);
    int perCraft = Math.max(1, result.getAmount());
    ItemStack resultSample = result.clone();
    resultSample.setAmount(1);
    return new CraftPlan(
        resultSample,
        perCraft,
        ingredients,
        remainderItems(craftResult),
        maxCraft,
        resultKey,
        false);
  }

  private ItemStack[] copyMatrix(ItemStack[] matrix) {
    ItemStack[] copy = new ItemStack[matrix.length];
    for (int i = 0; i < matrix.length; i++) {
      copy[i] = matrix[i] == null ? null : matrix[i].clone();
    }
    return copy;
  }

  private Map<String, CraftItem> remainderItems(ItemCraftResult craftResult) {
    Map<String, CraftItem> remainders = new HashMap<>();
    for (ItemStack remainder : craftResult.getResultingMatrix()) {
      addRemainder(remainders, remainder);
    }
    for (ItemStack remainder : craftResult.getOverflowItems()) {
      addRemainder(remainders, remainder);
    }
    return remainders;
  }

  private void addRemainder(Map<String, CraftItem> remainders, ItemStack remainder) {
    if (remainder == null || remainder.getType().isAir() || remainder.getAmount() <= 0) return;
    ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(remainder);
    CraftItem existing = remainders.get(data.key());
    if (existing == null) {
      remainders.put(data.key(), new CraftItem(data.key(), data.sample(), remainder.getAmount()));
    } else {
      remainders.put(
          data.key(),
          new CraftItem(
              data.key(), existing.sample(), existing.amountPerCraft() + remainder.getAmount()));
    }
  }

  private ItemStack clearButton() {
    ItemStack item = new ItemStack(Material.STRUCTURE_VOID);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.itemName(
          Component.text(tr("gui.crafting.clear")).decoration(TextDecoration.ITALIC, false));
      item.setItemMeta(meta);
    }
    return item;
  }

  private ItemStack storageCraftButton() {
    return craftButton(tr("gui.crafting.button.storage"), CraftingState.ConfirmTarget.STORAGE);
  }

  private ItemStack playerCraftButton() {
    return craftButton(tr("gui.crafting.button.player"), CraftingState.ConfirmTarget.PLAYER);
  }

  private ItemStack craftButton(String title, CraftingState.ConfirmTarget target) {
    Component name = Component.text(title).decoration(TextDecoration.ITALIC, false);
    List<Component> lore = new ArrayList<>();
    lore.add(
        Component.text(tr("gui.crafting.button.single")).decoration(TextDecoration.ITALIC, false));
    lore.add(
        Component.text(tr("gui.crafting.button.stack")).decoration(TextDecoration.ITALIC, false));
    lore.add(
        Component.text(tr("gui.crafting.button.all")).decoration(TextDecoration.ITALIC, false));
    int remaining = state.confirmRemaining(target, confirmTimeoutMs);
    if (remaining > 0) {
      lore.add(
          Component.text(tr("gui.crafting.button.all_warning"))
              .color(NamedTextColor.RED)
              .decoration(TextDecoration.ITALIC, false));
      lore.add(
          Component.text(tr("gui.crafting.button.all_confirm", remaining))
              .color(NamedTextColor.RED)
              .decoration(TextDecoration.ITALIC, false));
    }
    return target == CraftingState.ConfirmTarget.STORAGE
        ? GuiItems.craftStorageButton(name, lore, useFillers)
        : GuiItems.craftPlayerButton(name, lore, useFillers);
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

  private List<OutputStack> outputAdditions(CraftPlan plan, long outputAmount) {
    Map<String, OutputStack> additions = new HashMap<>();
    if (outputAmount > 0) {
      addOutput(additions, plan.resultKey, plan.result, outputAmount);
    }
    return new ArrayList<>(additions.values());
  }

  private List<OutputStack> remainderAdditions(CraftPlan plan, int crafts) {
    Map<String, OutputStack> additions = new HashMap<>();
    addRemainders(additions, plan, crafts);
    return new ArrayList<>(additions.values());
  }

  private void addRemainders(Map<String, OutputStack> additions, CraftPlan plan, int crafts) {
    if (crafts <= 0 || plan.remainders().isEmpty()) return;
    for (CraftItem remainder : plan.remainders().values()) {
      addOutput(
          additions,
          remainder.key(),
          remainder.sample(),
          Math.max(0L, (long) remainder.amountPerCraft() * crafts));
    }
  }

  private void addOutput(
      Map<String, OutputStack> additions, String key, ItemStack sample, long amount) {
    if (key == null || sample == null || sample.getType().isAir() || amount <= 0) return;
    OutputStack existing = additions.get(key);
    if (existing == null) {
      additions.put(key, new OutputStack(key, sample.clone(), amount));
      return;
    }
    additions.put(
        key, new OutputStack(key, existing.sample(), saturatingAdd(existing.amount(), amount)));
  }

  private boolean canStoreAdditions(List<OutputStack> additions) {
    long needed = 0L;
    for (OutputStack addition : additions) {
      if (addition.amount() <= 0) continue;
      long weight = Math.max(1L, cache.nestedWeight(addition.sample()));
      needed = saturatingAdd(needed, saturatingMultiply(addition.amount(), weight));
      if (needed > spaceLeft()) return false;
    }
    return needed <= spaceLeft();
  }

  private void addToStorage(List<OutputStack> additions) {
    for (OutputStack addition : additions) {
      if (addition.amount() > 0) {
        cache.addItem(addition.key(), addition.sample(), addition.amount());
      }
    }
  }

  private void deliverRemainders(CraftPlan plan, int crafts) {
    List<OutputStack> remainders = remainderAdditions(plan, crafts);
    if (remainders.isEmpty()) return;
    if (canStoreAdditions(remainders)) {
      addToStorage(remainders);
      return;
    }
    addAllToPlayerOrStorage(viewer, remainders);
  }

  private boolean canAddAllToPlayer(Player player, List<OutputStack> additions) {
    ItemStack[] simulated = new ItemStack[36];
    var inv = player.getInventory();
    for (int i = 0; i < simulated.length; i++) {
      ItemStack item = inv.getItem(i);
      simulated[i] = item == null ? null : item.clone();
    }
    for (OutputStack addition : additions) {
      long remaining = addition.amount();
      if (remaining < 0 || remaining > Integer.MAX_VALUE) return false;
      if (remaining == 0) continue;
      String key = addition.key();
      int maxStack = Math.max(1, addition.sample().getMaxStackSize());
      for (int i = 0; i < simulated.length && remaining > 0; i++) {
        ItemStack slot = simulated[i];
        if (slot == null || slot.getType() == Material.AIR) continue;
        if (!key.equals(ItemKeyUtil.keyFor(slot))) continue;
        int space = maxStack - slot.getAmount();
        if (space <= 0) continue;
        int move = (int) Math.min(space, remaining);
        slot.setAmount(slot.getAmount() + move);
        remaining -= move;
      }
      for (int i = 0; i < simulated.length && remaining > 0; i++) {
        ItemStack slot = simulated[i];
        if (slot != null && slot.getType() != Material.AIR) continue;
        int move = (int) Math.min(maxStack, remaining);
        ItemStack copy = addition.sample().clone();
        copy.setAmount(move);
        simulated[i] = copy;
        remaining -= move;
      }
      if (remaining > 0) return false;
    }
    return true;
  }

  private void addAllToPlayerOrStorage(Player player, List<OutputStack> additions) {
    for (OutputStack addition : additions) {
      long remaining = addition.amount();
      while (remaining > 0) {
        int move = (int) Math.min(Integer.MAX_VALUE, remaining);
        ItemStack stack = addition.sample().clone();
        stack.setAmount(move);
        int moved = addToPlayerInventory(player, stack);
        remaining -= moved;
        if (moved < move) {
          long leftover = move - moved + remaining;
          OutputStack leftoverStack = new OutputStack(addition.key(), addition.sample(), leftover);
          if (canStoreAdditions(List.of(leftoverStack))) {
            addToStorage(List.of(leftoverStack));
          } else {
            dropOutput(player, leftoverStack);
          }
          break;
        }
      }
    }
  }

  private void dropOutput(Player player, OutputStack stack) {
    long remaining = stack.amount();
    while (remaining > 0) {
      int move = (int) Math.min(Math.max(1, stack.sample().getMaxStackSize()), remaining);
      ItemStack drop = stack.sample().clone();
      drop.setAmount(move);
      player.getWorld().dropItemNaturally(player.getLocation(), drop);
      remaining -= move;
    }
  }

  private long saturatingAdd(long left, long right) {
    if (right <= 0) return left;
    if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
    return left + right;
  }

  private long saturatingMultiply(long left, long right) {
    if (left <= 0 || right <= 0) return 0L;
    if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
    return left * right;
  }

  private int addToPlayerInventory(Player player, ItemStack stack) {
    ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(stack);
    ItemStack sample = data.sample();
    return moveSampleToPlayerInventory(player, sample, data.key(), stack.getAmount());
  }

  private int maxAddableToPlayer(Player player, ItemStack sample, int desired) {
    int remaining = desired;
    int maxStack = sample.getMaxStackSize();
    String key = ItemKeyUtil.keyFor(sample);
    var inv = player.getInventory();
    for (int i = 0; i < 36 && remaining > 0; i++) {
      ItemStack item = inv.getItem(i);
      if (item == null || item.getType() == Material.AIR) continue;
      if (!key.equals(ItemKeyUtil.keyFor(item))) continue;
      int space = maxStack - item.getAmount();
      if (space <= 0) continue;
      int move = Math.min(space, remaining);
      remaining -= move;
    }
    int emptySlots = 0;
    for (int i = 0; i < 36; i++) {
      ItemStack item = inv.getItem(i);
      if (item == null || item.getType() == Material.AIR) emptySlots++;
    }
    int capacity = emptySlots * maxStack;
    int totalFit = desired - remaining;
    totalFit += Math.min(capacity, remaining);
    return totalFit;
  }

  private int craftIndex(int rawSlot) {
    if (rawSlot < 0 || rawSlot >= CRAFT_INDEX.length) return -1;
    return CRAFT_INDEX[rawSlot];
  }

  private boolean isStorageSlot(int rawSlot) {
    if (rawSlot < 0 || rawSlot >= STORAGE_SLOT.length) return false;
    return STORAGE_SLOT[rawSlot];
  }

  private boolean hasCraftItems(ItemStack[] grid) {
    for (ItemStack stack : grid) {
      if (stack != null && stack.getType() != Material.AIR) {
        return true;
      }
    }
    return false;
  }

  private record Ingredient(String key, ItemStack sample, int perCraft) {}

  private record CraftItem(String key, ItemStack sample, int amountPerCraft) {}

  private record OutputStack(String key, ItemStack sample, long amount) {}

  private record CraftPlan(
      ItemStack result,
      int resultPerCraft,
      Map<String, Ingredient> ingredients,
      Map<String, CraftItem> remainders,
      int maxCraft,
      String resultKey,
      boolean clearGrid) {}

  private record StackCraft(int give, int bufferUsed, int crafts) {}

  private boolean hasBufferedOutput() {
    return state.snapshotBuffer() != null;
  }

  private boolean flushBufferToStorage() {
    CraftingState.Buffer buffer = state.snapshotBuffer();
    if (buffer == null) {
      state.clearBuffer();
      return false;
    }
    OutputStack addition = new OutputStack(buffer.key(), buffer.sample(), buffer.amount());
    if (!canStoreAdditions(List.of(addition))) {
      setInfoErrorMessage(null);
      return false;
    }
    cache.addItem(buffer.key(), buffer.sample(), buffer.amount());
    state.clearBuffer();
    return true;
  }

  private void flushBufferToPlayerOrDrop() {
    CraftingState.Buffer buffer = state.snapshotBuffer();
    if (buffer == null) return;
    OutputStack output = new OutputStack(buffer.key(), buffer.sample(), buffer.amount());
    List<OutputStack> additions = List.of(output);
    if (canAddAllToPlayer(viewer, additions)) {
      addAllToPlayerOrStorage(viewer, additions);
    } else {
      dropOutput(viewer, output);
    }
    state.clearBuffer();
  }
}
