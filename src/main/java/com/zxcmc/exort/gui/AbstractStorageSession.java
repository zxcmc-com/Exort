package com.zxcmc.exort.gui;

import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageDisplayName;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Shared storage session logic: search/sort/paging and bossbar state. Subclasses only need to
 * render their specific layouts.
 */
public abstract class AbstractStorageSession implements SearchableSession {
  protected final Player viewer;
  protected final StorageCache cache;
  protected final StorageTier tier;
  protected final Lang lang;
  protected final ItemNameService itemNames;
  protected final StorageKeys keys;
  protected final SessionManager manager;
  protected final Block terminalBlock;
  protected final Location storageLocation;
  protected final Inventory inventory;
  protected final BossBar bossBar;
  protected boolean readOnly;
  protected SortMode sortMode = SortMode.AMOUNT;
  protected boolean sortFrozen;
  protected List<String> sortOrder = new ArrayList<>();
  protected SearchQuery searchQuery = SearchQuery.empty();
  protected List<DisplayEntry> displayList = List.of();
  protected List<Integer> displayCategories = List.of();
  protected int page;
  protected int searchResultsCount;
  protected boolean displayListTruncated;
  private long lastBuildVersion = -1;
  private SortMode lastBuildSortMode;
  private boolean lastBuildSortFrozen;
  private SearchQuery lastBuildSearchQuery;
  private int lastBuildPage = -1;
  private long lastBuildNameServiceVersion = -1L;
  private String lastBuildItemLanguage = "";
  protected final boolean wireless;
  private static final long WIRELESS_REFRESH_TICKS = 40L;
  private int wirelessRefreshTaskId = -1;
  private boolean wirelessForceRebuild;
  private long wirelessRefreshUntilMs;

  protected AbstractStorageSession(
      Player viewer,
      StorageCache cache,
      StorageTier tier,
      Lang lang,
      ItemNameService itemNames,
      StorageKeys keys,
      SessionManager manager,
      Block terminalBlock,
      Location storageLocation,
      boolean readOnly,
      Component title,
      int inventorySize,
      SortMode sortMode,
      boolean wireless) {
    this.viewer = viewer;
    this.cache = cache;
    this.tier = tier;
    this.lang = lang;
    this.itemNames = itemNames;
    this.keys = keys;
    this.manager = manager;
    this.terminalBlock = terminalBlock;
    this.storageLocation = storageLocation;
    this.readOnly = readOnly;
    this.wireless = wireless;
    if (sortMode != null) {
      this.sortMode = sortMode;
    }
    this.inventory = Bukkit.createInventory(this, inventorySize, title);
    this.bossBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10);
    bossBar.addPlayer(viewer);
    bossBar.setVisible(true);
    updateBossBar();
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  @Override
  public Player getViewer() {
    return viewer;
  }

  @Override
  public StorageCache getCache() {
    return cache;
  }

  @Override
  public StorageTier getTier() {
    return tier;
  }

  @Override
  public String getStorageId() {
    return cache.getStorageId();
  }

  @Override
  public Block getTerminalBlock() {
    return terminalBlock;
  }

  @Override
  public Location getStorageLocation() {
    return storageLocation;
  }

  public boolean isWireless() {
    return wireless;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
    updateBossBar();
  }

  public void setSortMode(SortMode mode) {
    if (mode != null) {
      this.sortMode = mode;
    }
  }

  @Override
  public String getSearchQuery() {
    return searchQuery.displayText();
  }

  @Override
  public void setSearchQuery(String query) {
    searchQuery = SearchQuery.from(query);
    page = 0;
  }

  @Override
  public void clearSearch() {
    setSearchQuery(null);
  }

  protected boolean hasSearch() {
    return !searchQuery.isEmpty();
  }

  protected boolean isSearchResultsPage() {
    return hasSearch() && page < searchPages();
  }

  protected boolean isDisplayListTruncated() {
    return displayListTruncated;
  }

  protected int pageSize() {
    return GuiLayout.PAGE_SIZE;
  }

  protected DisplayEntry entryAt(int index) {
    if (index < 0 || index >= displayList.size()) return null;
    return displayList.get(index);
  }

  protected boolean matchesQuery(ItemStack stack) {
    return searchQuery.matches(stack, itemNames, lang, keys, itemDictionaryLanguage());
  }

  protected List<DisplayEntry> buildDisplayList() {
    long version = cache.version();
    String itemLanguage = itemDictionaryLanguage();
    long nameServiceVersion = itemNames == null ? 0L : itemNames.version(itemLanguage);
    if (!wirelessForceRebuild
        && version == lastBuildVersion
        && nameServiceVersion == lastBuildNameServiceVersion
        && Objects.equals(itemLanguage, lastBuildItemLanguage)
        && sortMode == lastBuildSortMode
        && sortFrozen == lastBuildSortFrozen
        && page == lastBuildPage
        && Objects.equals(searchQuery, lastBuildSearchQuery)) {
      return displayList;
    }
    wirelessRefreshUntilMs = 0L;
    wirelessForceRebuild = false;
    StorageDisplayListBuilder.Result result =
        PerfStats.measure(
            PerfStats.Area.GUI,
            () ->
                StorageDisplayListBuilder.build(
                    cache.itemsSnapshot(),
                    sortMode,
                    sortFrozen,
                    sortOrder,
                    searchQuery,
                    itemNames,
                    lang,
                    keys,
                    itemLanguage,
                    page,
                    pageSize(),
                    StorageDisplayListBuilder.DEFAULT_MAX_DISPLAY_ENTRIES,
                    this::displaySample));
    sortOrder = result.sortOrder();
    searchResultsCount = result.searchResultsCount();
    displayCategories = result.displayCategories();
    displayListTruncated = result.truncated();
    rememberBuildCache(version, nameServiceVersion, itemLanguage);
    return result.displayList();
  }

  private void rememberBuildCache(long version, long nameServiceVersion, String itemLanguage) {
    lastBuildVersion = version;
    lastBuildNameServiceVersion = nameServiceVersion;
    lastBuildItemLanguage = itemLanguage;
    lastBuildSortMode = sortMode;
    lastBuildSortFrozen = sortFrozen;
    lastBuildSearchQuery = searchQuery;
    lastBuildPage = page;
  }

  private void maybeMarkWirelessRefresh(WirelessTerminalService ws, ItemStack sample) {
    long endAt = ws.chargingEndsAtMillis(sample);
    if (endAt <= 0) return;
    long until = endAt + 10_000L;
    if (until > wirelessRefreshUntilMs) {
      wirelessRefreshUntilMs = until;
    }
  }

  protected void handleWirelessRefreshAfterRender() {
    if (wirelessRefreshUntilMs > System.currentTimeMillis()) {
      scheduleWirelessRefresh();
      return;
    }
    cancelWirelessRefreshTask();
  }

  private void scheduleWirelessRefresh() {
    if (wirelessRefreshTaskId != -1) return;
    wirelessRefreshTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                manager.plugin(),
                () -> {
                  if (!viewer.isOnline()) {
                    cancelWirelessRefreshTask();
                    return;
                  }
                  if (wirelessRefreshUntilMs <= System.currentTimeMillis()) {
                    cancelWirelessRefreshTask();
                    return;
                  }
                  wirelessForceRebuild = true;
                  render();
                },
                WIRELESS_REFRESH_TICKS,
                WIRELESS_REFRESH_TICKS);
  }

  protected void cancelWirelessRefreshTask() {
    if (wirelessRefreshTaskId != -1) {
      Bukkit.getScheduler().cancelTask(wirelessRefreshTaskId);
      wirelessRefreshTaskId = -1;
    }
  }

  private ItemStack displaySample(StorageCache.StorageItem item) {
    ItemStack sample = item.sample();
    WirelessTerminalService ws = manager.wirelessService();
    if (ws != null && ws.isWireless(sample)) {
      sample = ws.displaySample(sample);
      maybeMarkWirelessRefresh(ws, sample);
    }
    return sample;
  }

  protected String currentPageCategoryLabel() {
    if (sortMode != SortMode.CATEGORY || displayCategories.isEmpty()) {
      return null;
    }
    int pageSize = pageSize();
    int start = page * pageSize;
    int end = Math.min(displayCategories.size(), start + pageSize);
    for (int i = start; i < end; i++) {
      Integer category = displayCategories.get(i);
      if (category != null) {
        return CategoryLabels.labelForIndex(category, lang, viewer);
      }
    }
    return null;
  }

  protected String currentPageSearchLabel() {
    if (!hasSearch()) {
      return null;
    }
    int searchPages = searchPages();
    if (page < searchPages) {
      return tr("gui.search.results");
    }
    return null;
  }

  protected int searchPages() {
    if (!hasSearch()) {
      return 0;
    }
    int pageSize = pageSize();
    int pages = (int) Math.ceil(searchResultsCount / (double) pageSize);
    return Math.max(1, pages);
  }

  protected void updateBossBar() {
    long current = cache.effectiveTotal();
    long max = Math.max(1, tier.maxItems());
    double progress = Math.min(1.0, Math.max(0.0, (double) current / (double) max));
    double free = 1.0 - progress;
    String percent = FORMAT_PERCENT.format(progress * 100.0) + "%";
    bossBar.setTitle(
        tr(
            "gui.bossbar",
            StorageDisplayName.label(lang, viewer, tier, cache.getDisplayName()),
            formatNumber(current),
            formatNumber(max),
            percent));
    bossBar.setColor(readOnly ? BarColor.RED : freeColorBar(free));
    bossBar.setProgress(progress);
  }

  protected String formatNumber(long value) {
    return FORMAT_NUMBER.format(value);
  }

  protected NamedTextColor freeColor(double freeRatio) {
    if (freeRatio <= 0.05) {
      return NamedTextColor.RED;
    }
    if (freeRatio <= 0.30) {
      return NamedTextColor.GOLD;
    }
    return NamedTextColor.GREEN;
  }

  protected BarColor freeColorBar(double freeRatio) {
    if (freeRatio <= 0.05) {
      return BarColor.RED;
    }
    if (freeRatio <= 0.30) {
      return BarColor.YELLOW;
    }
    return BarColor.GREEN;
  }

  protected static final DecimalFormat FORMAT_NUMBER = new DecimalFormat("#,###");
  protected static final DecimalFormat FORMAT_PERCENT = new DecimalFormat("0.0");

  protected String infoErrorMessage;

  protected void setInfoErrorMessage(String message) {
    this.infoErrorMessage = message;
  }

  protected abstract void triggerInfoError();

  protected String tr(String key, Object... params) {
    return lang.tr(viewer, key, params);
  }

  protected String itemDictionaryLanguage() {
    if (itemNames == null) {
      return lang.configuredLanguage();
    }
    return itemNames.dictionaryLanguage(viewer.locale().toString(), lang.configuredLanguage());
  }

  protected long depositFromStack(ItemStack stack) {
    var ws = manager.wirelessService();
    if (ws != null) {
      if (isWireless() && ws.isWireless(stack)) {
        String message = tr("message.wireless.self_store");
        setInfoErrorMessage(message);
        triggerInfoError();
        manager.playerFeedback().errorMessage(viewer, message);
        return 0;
      }
    }
    ItemStack storageStack = stack.clone();
    if (ws != null && ws.isWireless(storageStack)) {
      ws.prepareForStorage(storageStack);
    }
    ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(storageStack);
    String key = data.key();
    ItemStack sample = data.sample();
    long allowed = Math.min(stack.getAmount(), spaceLeftFor(sample));
    if (allowed <= 0) return 0;
    cache.addItem(key, sample, allowed);
    return allowed;
  }

  protected long depositFromCursor(ItemStack cursor, int intended, InventoryClickEvent event) {
    var ws = manager.wirelessService();
    if (ws != null) {
      if (isWireless() && ws.isWireless(cursor)) {
        String message = tr("message.wireless.self_store");
        setInfoErrorMessage(message);
        triggerInfoError();
        manager.playerFeedback().errorMessage(viewer, message);
        return 0;
      }
    }
    ItemStack storageStack = cursor.clone();
    storageStack.setAmount(Math.max(1, intended));
    if (ws != null && ws.isWireless(storageStack)) {
      ws.prepareForStorage(storageStack);
    }
    long allowed = Math.min(intended, spaceLeftFor(storageStack));
    if (allowed <= 0) {
      setInfoErrorMessage(null);
      triggerInfoError();
      return 0;
    }
    if (allowed < intended) {
      setInfoErrorMessage(null);
      triggerInfoError();
    }
    ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(storageStack);
    String key = data.key();
    ItemStack sample = data.sample();
    cache.addItem(key, sample, allowed);
    cursor.setAmount(cursor.getAmount() - (int) allowed);
    if (cursor.getAmount() <= 0) {
      event.getView().setCursor(new ItemStack(Material.AIR));
    } else {
      event.getView().setCursor(cursor);
    }
    return allowed;
  }

  protected void handleBottomInventoryShiftDeposit(InventoryClickEvent event) {
    if (readOnly && event.isShiftClick()) {
      event.setCancelled(true);
      return;
    }
    if (!event.isShiftClick()) {
      return;
    }
    ItemStack clicked = event.getCurrentItem();
    if (clicked == null || clicked.getType() == Material.AIR) {
      return;
    }
    setInfoErrorMessage(null);
    long deposited = depositFromStack(clicked);
    if (deposited > 0) {
      if (deposited < clicked.getAmount()) {
        triggerInfoError();
      }
      int remaining = (int) (clicked.getAmount() - deposited);
      if (remaining <= 0) {
        event.setCurrentItem(null);
      } else {
        clicked.setAmount(remaining);
        event.setCurrentItem(clicked);
      }
      manager.renderStorage(cache.getStorageId(), SortEvent.DEPOSIT);
    } else if (infoErrorMessage == null || infoErrorMessage.isBlank()) {
      triggerInfoError();
    }
    event.setCancelled(true);
  }

  protected boolean handleStorageSlotTransfer(
      InventoryClickEvent event,
      DisplayEntry entry,
      SortEvent noMoveEvent,
      SortEvent withdrawEvent) {
    if (readOnly) {
      return false;
    }
    ItemStack cursor = event.getView().getCursor();
    if (cursor != null && cursor.getType() != Material.AIR) {
      setInfoErrorMessage(null);
      int moveAmount = event.isRightClick() ? 1 : cursor.getAmount();
      moveAmount = Math.min(moveAmount, cursor.getAmount());
      long deposited = depositFromCursor(cursor, moveAmount, event);
      if (deposited > 0) {
        manager.renderStorage(cache.getStorageId(), SortEvent.DEPOSIT);
      }
      return true;
    }

    if (entry == null) {
      return false;
    }

    if (event.isShiftClick()) {
      var reserved = cache.reserveItem(entry.itemKey(), entry.amount()).orElse(null);
      if (reserved == null) {
        manager.renderStorage(cache.getStorageId(), noMoveEvent);
        return true;
      }
      int moved = moveToInventory(event.getWhoClicked(), reserved, entry.amount());
      rollbackReserved(reserved, moved);
      manager.renderStorage(cache.getStorageId(), moved > 0 ? withdrawEvent : noMoveEvent);
      return true;
    }

    int desired = entry.amount();
    if (event.isRightClick()) {
      desired = (desired + 1) / 2;
    }
    var reserved = cache.reserveItem(entry.itemKey(), desired).orElse(null);
    if (reserved == null) {
      manager.renderStorage(cache.getStorageId(), noMoveEvent);
      return true;
    }
    int given = moveToCursor(event.getWhoClicked(), reserved, desired, event);
    rollbackReserved(reserved, given);
    manager.renderStorage(cache.getStorageId(), given > 0 ? withdrawEvent : noMoveEvent);
    return true;
  }

  protected long spaceLeft() {
    long space = tier.maxItems() - cache.effectiveTotal();
    return Math.max(0, space);
  }

  protected long spaceLeftFor(ItemStack stack) {
    long remaining = spaceLeft();
    long weight = Math.max(1, cache.nestedWeight(stack));
    return remaining / weight;
  }

  protected int moveToCursor(
      HumanEntity who,
      StorageCache.ReservedItem reserved,
      int desiredAmount,
      InventoryClickEvent event) {
    if (reserved == null || reserved.amount() <= 0) return 0;
    ItemStack cursor = event.getView().getCursor();
    ItemStack give = reserved.sample().clone();
    var ws = manager.wirelessService();
    if (ws != null) {
      give = ws.extractFromStorage(give);
    }
    String giveKey = ItemKeyUtil.keyFor(give);
    int max = give.getMaxStackSize();
    int moved = 0;
    if (cursor == null || cursor.getType() == Material.AIR) {
      int take =
          Math.min(
              Math.min(desiredAmount, max), (int) Math.min(Integer.MAX_VALUE, reserved.amount()));
      give.setAmount(take);
      event.getView().setCursor(give);
      moved = take;
    } else {
      String cursorKey = ItemKeyUtil.keyFor(cursor);
      if (!cursorKey.equals(giveKey)) {
        return 0;
      }
      int space = max - cursor.getAmount();
      if (space <= 0) return 0;
      int take =
          Math.min(
              Math.min(space, desiredAmount), (int) Math.min(Integer.MAX_VALUE, reserved.amount()));
      cursor.setAmount(cursor.getAmount() + take);
      event.getView().setCursor(cursor);
      moved = take;
    }
    return moved;
  }

  protected int moveToInventory(HumanEntity who, StorageCache.ReservedItem reserved, int amount) {
    if (reserved == null || reserved.amount() <= 0) return 0;
    if (!(who instanceof Player player)) return 0;
    ItemStack sample = reserved.sample().clone();
    var ws = manager.wirelessService();
    if (ws != null) {
      sample = ws.extractFromStorage(sample);
    }
    String key = ItemKeyUtil.keyFor(sample);
    int desired = Math.min(amount, (int) Math.min(Integer.MAX_VALUE, reserved.amount()));
    return moveSampleToPlayerInventory(player, sample, key, desired);
  }

  protected static int moveSampleToPlayerInventory(
      Player player, ItemStack sample, String key, int amount) {
    if (player == null || sample == null || sample.getType() == Material.AIR || amount <= 0) {
      return 0;
    }
    var inv = player.getInventory();
    int moved = 0;
    int remaining = amount;
    int maxStack = Math.max(1, sample.getMaxStackSize());
    for (int i = 0; i < 36 && remaining > 0; i++) {
      ItemStack item = inv.getItem(i);
      if (item == null || item.getType() == Material.AIR) continue;
      if (!key.equals(ItemKeyUtil.keyFor(item))) continue;
      int space = maxStack - item.getAmount();
      if (space <= 0) continue;
      int move = Math.min(space, remaining);
      item.setAmount(item.getAmount() + move);
      inv.setItem(i, item);
      remaining -= move;
      moved += move;
    }
    for (int i = 0; i < 36 && remaining > 0; i++) {
      ItemStack item = inv.getItem(i);
      if (item != null && item.getType() != Material.AIR) continue;
      int move = Math.min(maxStack, remaining);
      ItemStack stack = sample.clone();
      stack.setAmount(move);
      inv.setItem(i, stack);
      remaining -= move;
      moved += move;
    }
    return moved;
  }

  protected void rollbackReserved(StorageCache.ReservedItem reserved, int moved) {
    if (reserved == null) return;
    long rollback = reserved.amount() - Math.max(0, moved);
    if (rollback > 0) {
      cache.addItem(reserved.key(), reserved.sample(), rollback);
    }
  }
}
