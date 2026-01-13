package com.zxcmc.exort.gui;

import com.zxcmc.exort.core.i18n.ItemNameService;
import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.items.ItemKeyUtil;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.text.DecimalFormat;
import java.util.*;
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
  protected final SessionManager manager;
  protected final Block terminalBlock;
  protected final Location storageLocation;
  protected final Inventory inventory;
  protected final BossBar bossBar;
  protected boolean readOnly;
  protected SortMode sortMode = SortMode.AMOUNT;
  protected boolean sortFrozen;
  protected List<String> sortOrder = new ArrayList<>();
  protected String searchQuery;
  protected List<String> searchTokens = List.of();
  protected List<DisplayEntry> displayList = List.of();
  protected List<Integer> displayCategories = List.of();
  protected int page;
  protected int searchResultsCount;
  private long lastBuildVersion = -1;
  private SortMode lastBuildSortMode;
  private boolean lastBuildSortFrozen;
  private String lastBuildSearchQuery;
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
    return searchQuery;
  }

  @Override
  public void setSearchQuery(String query) {
    List<String> tokens = SortSearchHelper.normalizeSearchTokens(query);
    if (tokens.isEmpty()) {
      searchQuery = null;
      searchTokens = List.of();
    } else {
      searchQuery = String.join("\n", tokens);
      List<String> lowered = new ArrayList<>(tokens.size());
      for (String token : tokens) {
        lowered.add(token.toLowerCase(Locale.ROOT));
      }
      searchTokens = lowered;
    }
    page = 0;
  }

  @Override
  public void clearSearch() {
    setSearchQuery(null);
  }

  protected boolean hasSearch() {
    return searchTokens != null && !searchTokens.isEmpty();
  }

  protected boolean isSearchResultsPage() {
    return hasSearch() && page < searchPages();
  }

  protected int pageSize() {
    return GuiLayout.PAGE_SIZE;
  }

  protected DisplayEntry entryAt(int index) {
    if (index < 0 || index >= displayList.size()) return null;
    return displayList.get(index);
  }

  protected boolean matchesQuery(ItemStack stack) {
    return SortSearchHelper.matchesQuery(stack, searchTokens, itemNames);
  }

  protected List<DisplayEntry> buildDisplayList() {
    long version = cache.version();
    if (!wirelessForceRebuild
        && version == lastBuildVersion
        && sortMode == lastBuildSortMode
        && sortFrozen == lastBuildSortFrozen
        && Objects.equals(searchQuery, lastBuildSearchQuery)) {
      return displayList;
    }
    wirelessRefreshUntilMs = 0L;
    wirelessForceRebuild = false;
    searchResultsCount = 0;
    List<DisplayEntry> list = new ArrayList<>();
    List<Integer> categories = sortMode == SortMode.CATEGORY ? new ArrayList<>() : List.of();
    List<StorageCache.StorageItem> items = cache.itemsSnapshot();
    boolean allowCategoryDupes = sortMode == SortMode.CATEGORY && !hasSearch();
    SortSearchHelper.SortResult orderedResult =
        SortSearchHelper.resolveOrder(
            items, sortMode, sortFrozen, sortOrder, itemNames, allowCategoryDupes);
    sortOrder = orderedResult.order();
    List<StorageCache.StorageItem> ordered = orderedResult.ordered();
    if (!hasSearch()) {
      if (sortMode == SortMode.CATEGORY) {
        appendEntriesWithCategoryPadding(list, categories, ordered, allowCategoryDupes);
      } else {
        appendEntries(list, ordered);
      }
      displayCategories = categories;
      rememberBuildCache(version);
      return list;
    }
    List<StorageCache.StorageItem> matches = new ArrayList<>();
    List<StorageCache.StorageItem> others = new ArrayList<>();
    // Cache search candidates for this render to avoid repeated dictionary/item-meta lookups.
    Map<String, List<String>> candidatesCache = new HashMap<>();
    for (StorageCache.StorageItem item : ordered) {
      if (matchesQueryCached(item, candidatesCache)) {
        matches.add(item);
      } else {
        others.add(item);
      }
    }
    int pageSize = pageSize();
    if (matches.isEmpty()) {
      // Keep an empty "search results" page when nothing matches.
      for (int i = 0; i < pageSize; i++) {
        list.add(null);
        if (sortMode == SortMode.CATEGORY) {
          categories.add(null);
        }
      }
      searchResultsCount = 0;
    } else {
      if (sortMode == SortMode.CATEGORY) {
        appendEntriesWithCategories(list, categories, matches, false);
      } else {
        appendEntries(list, matches);
      }
      searchResultsCount = list.size();
      int pad = (pageSize - (searchResultsCount % pageSize)) % pageSize;
      for (int i = 0; i < pad; i++) {
        list.add(null);
        if (sortMode == SortMode.CATEGORY) {
          categories.add(null);
        }
      }
    }
    if (sortMode == SortMode.CATEGORY) {
      appendEntriesWithCategoryPadding(list, categories, others, false);
    } else {
      appendEntries(list, others);
    }
    displayCategories = categories;
    rememberBuildCache(version);
    return list;
  }

  private void rememberBuildCache(long version) {
    lastBuildVersion = version;
    lastBuildSortMode = sortMode;
    lastBuildSortFrozen = sortFrozen;
    lastBuildSearchQuery = searchQuery;
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
                manager.getPlugin(),
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

  private boolean matchesQueryCached(
      StorageCache.StorageItem item, Map<String, List<String>> candidatesCache) {
    if (searchTokens == null || searchTokens.isEmpty()) return true;
    List<String> candidates =
        candidatesCache.computeIfAbsent(
            item.key(), key -> SortSearchHelper.buildSearchCandidates(item.sample(), itemNames));
    if (candidates.isEmpty()) return true;
    for (String token : searchTokens) {
      for (String candidate : candidates) {
        if (candidate.contains(token)) {
          return true;
        }
      }
    }
    return false;
  }

  protected void appendEntries(List<DisplayEntry> list, List<StorageCache.StorageItem> items) {
    for (StorageCache.StorageItem item : items) {
      ItemStack sample = item.sample();
      WirelessTerminalService ws = manager.getPlugin().getWirelessService();
      if (ws != null && ws.isWireless(sample)) {
        sample = ws.displaySample(sample);
        maybeMarkWirelessRefresh(ws, sample);
      }
      int maxStack = Math.max(1, sample.getMaxStackSize());
      long remaining = item.amount();
      while (remaining > 0) {
        int amount = (int) Math.min(maxStack, remaining);
        list.add(new DisplayEntry(item.key(), sample, amount));
        remaining -= amount;
      }
    }
  }

  protected void appendEntriesWithCategories(
      List<DisplayEntry> list,
      List<Integer> categories,
      List<StorageCache.StorageItem> items,
      boolean allowCategoryDupes) {
    CreativeTabOrder order = CreativeTabOrder.get();
    Map<String, Integer> seen = new HashMap<>();
    for (StorageCache.StorageItem item : items) {
      int category = categoryFor(item, order, seen, allowCategoryDupes);
      appendEntriesWithCategory(list, categories, item, category);
    }
  }

  protected void appendEntriesWithCategory(
      List<DisplayEntry> list,
      List<Integer> categories,
      StorageCache.StorageItem item,
      int category) {
    ItemStack sample = item.sample();
    WirelessTerminalService ws = manager.getPlugin().getWirelessService();
    if (ws != null && ws.isWireless(sample)) {
      sample = ws.displaySample(sample);
      maybeMarkWirelessRefresh(ws, sample);
    }
    int maxStack = Math.max(1, sample.getMaxStackSize());
    long remaining = item.amount();
    while (remaining > 0) {
      int amount = (int) Math.min(maxStack, remaining);
      list.add(new DisplayEntry(item.key(), sample, amount));
      categories.add(category);
      remaining -= amount;
    }
  }

  protected void appendEntriesWithCategoryPadding(
      List<DisplayEntry> list,
      List<Integer> categories,
      List<StorageCache.StorageItem> items,
      boolean allowCategoryDupes) {
    if (items.isEmpty()) {
      return;
    }
    CreativeTabOrder order = CreativeTabOrder.get();
    Map<String, Integer> seen = new HashMap<>();
    int lastCategory = Integer.MIN_VALUE;
    int pageSize = pageSize();
    for (StorageCache.StorageItem item : items) {
      int category = categoryFor(item, order, seen, allowCategoryDupes);
      if (lastCategory != Integer.MIN_VALUE && category != lastCategory) {
        int pad = (pageSize - (list.size() % pageSize)) % pageSize;
        for (int i = 0; i < pad; i++) {
          list.add(null);
          categories.add(null);
        }
      }
      lastCategory = category;
      appendEntriesWithCategory(list, categories, item, category);
    }
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
        return CategoryLabels.labelForIndex(category, lang);
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
      return lang.tr("gui.search.results");
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

  protected int categoryFor(
      StorageCache.StorageItem item,
      CreativeTabOrder order,
      Map<String, Integer> seen,
      boolean allowCategoryDupes) {
    if (order == null || item == null) {
      return SortSearchHelper.categoryIndex(item != null ? item.sample() : null);
    }
    if (!allowCategoryDupes) {
      return order.positionFor(item.sample()).tabIndex();
    }
    int idx = seen.getOrDefault(item.key(), 0);
    seen.put(item.key(), idx + 1);
    List<CreativeTabOrder.Position> positions = order.positionsFor(item.sample());
    if (positions != null && idx < positions.size()) {
      return positions.get(idx).tabIndex();
    }
    return order.positionFor(item.sample()).tabIndex();
  }

  protected void updateBossBar() {
    long current = cache.effectiveTotal();
    long max = Math.max(1, tier.maxItems());
    double progress = Math.min(1.0, Math.max(0.0, (double) current / (double) max));
    double free = 1.0 - progress;
    String percent = FORMAT_PERCENT.format(progress * 100.0) + "%";
    bossBar.setTitle(
        lang.tr(
            "gui.bossbar", tier.displayName(), formatNumber(current), formatNumber(max), percent));
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

  protected long depositFromStack(ItemStack stack) {
    var ws = manager.getPlugin().getWirelessService();
    if (ws != null) {
      if (isWireless() && ws.isWireless(stack)) {
        String message = manager.getPlugin().getLang().tr("message.wireless.self_store");
        setInfoErrorMessage(message);
        triggerInfoError();
        manager
            .getPlugin()
            .getBossBarManager()
            .showError(viewer, message, manager.getPlugin().getStoragePeekTicks());
        return 0;
      }
      ws.prepareForStorage(stack);
    }
    ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(stack);
    String key = data.key();
    ItemStack sample = data.sample();
    long allowed = Math.min(stack.getAmount(), spaceLeftFor(sample));
    if (allowed <= 0) return 0;
    cache.addItem(key, sample, allowed);
    return allowed;
  }

  protected long depositFromCursor(ItemStack cursor, int intended, InventoryClickEvent event) {
    var ws = manager.getPlugin().getWirelessService();
    if (ws != null) {
      if (isWireless() && ws.isWireless(cursor)) {
        String message = manager.getPlugin().getLang().tr("message.wireless.self_store");
        setInfoErrorMessage(message);
        triggerInfoError();
        manager
            .getPlugin()
            .getBossBarManager()
            .showError(viewer, message, manager.getPlugin().getStoragePeekTicks());
        return 0;
      }
      ws.prepareForStorage(cursor);
    }
    long allowed = Math.min(intended, spaceLeftFor(cursor));
    if (allowed <= 0) {
      setInfoErrorMessage(null);
      triggerInfoError();
      return 0;
    }
    if (allowed < intended) {
      setInfoErrorMessage(null);
      triggerInfoError();
    }
    ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(cursor);
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
      HumanEntity who, DisplayEntry entry, int desiredAmount, InventoryClickEvent event) {
    ItemStack cursor = event.getView().getCursor();
    ItemStack give = entry.sample().clone();
    var ws = manager.getPlugin().getWirelessService();
    if (ws != null) {
      give = ws.extractFromStorage(give);
    }
    int max = give.getMaxStackSize();
    int moved = 0;
    if (cursor == null || cursor.getType() == Material.AIR) {
      int take = Math.min(desiredAmount, max);
      give.setAmount(take);
      event.getView().setCursor(give);
      moved = take;
    } else {
      String cursorKey = ItemKeyUtil.keyFor(cursor);
      if (!cursorKey.equals(entry.itemKey())) {
        return 0;
      }
      int space = max - cursor.getAmount();
      if (space <= 0) return 0;
      int take = Math.min(space, desiredAmount);
      cursor.setAmount(cursor.getAmount() + take);
      event.getView().setCursor(cursor);
      moved = take;
    }
    return moved;
  }

  protected int moveToInventory(HumanEntity who, ItemStack sample, String key, int amount) {
    if (!(who instanceof Player player)) return 0;
    var ws = manager.getPlugin().getWirelessService();
    if (ws != null) {
      sample = ws.extractFromStorage(sample);
    }
    var inv = player.getInventory();
    int moved = 0;
    int remaining = amount;
    int maxStack = sample.getMaxStackSize();
    // Fill existing stacks
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
    while (remaining > 0 && inv.firstEmpty() != -1) {
      int move = Math.min(maxStack, remaining);
      ItemStack stack = sample.clone();
      stack.setAmount(move);
      inv.addItem(stack);
      remaining -= move;
      moved += move;
    }
    return moved;
  }
}
