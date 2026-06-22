package com.zxcmc.exort.gui;

import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageCache;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.inventory.ItemStack;

final class StorageDisplayListBuilder {
  static final int DEFAULT_MAX_DISPLAY_ENTRIES = Integer.MAX_VALUE;

  private StorageDisplayListBuilder() {}

  static Result build(
      List<StorageCache.StorageItem> items,
      SortMode sortMode,
      boolean sortFrozen,
      List<String> previousSortOrder,
      SearchQuery searchQuery,
      ItemNameService itemNames,
      String itemLanguage,
      int requestedPage,
      int pageSize,
      int maxDisplayEntries,
      Function<StorageCache.StorageItem, ItemStack> displaySample) {
    return build(
        items,
        sortMode,
        sortFrozen,
        previousSortOrder,
        searchQuery,
        itemNames,
        null,
        null,
        itemLanguage,
        requestedPage,
        pageSize,
        maxDisplayEntries,
        displaySample);
  }

  static Result build(
      List<StorageCache.StorageItem> items,
      SortMode sortMode,
      boolean sortFrozen,
      List<String> previousSortOrder,
      SearchQuery searchQuery,
      ItemNameService itemNames,
      Lang lang,
      StorageKeys keys,
      String itemLanguage,
      int requestedPage,
      int pageSize,
      int maxDisplayEntries,
      Function<StorageCache.StorageItem, ItemStack> displaySample) {
    boolean hasSearch = searchQuery != null && !searchQuery.isEmpty();
    boolean allowCategoryDupes = sortMode == SortMode.CATEGORY && !hasSearch;
    SortSearchHelper.SortResult orderedResult =
        PerfStats.measure(
            "gui.sort",
            () ->
                SortSearchHelper.resolveOrder(
                    items,
                    sortMode,
                    sortFrozen,
                    previousSortOrder,
                    itemNames,
                    lang,
                    keys,
                    itemLanguage,
                    allowCategoryDupes));
    List<String> sortOrder = orderedResult.order();
    List<StorageCache.StorageItem> ordered = orderedResult.ordered();
    WindowAppender appender =
        new WindowAppender(
            requestedPage, pageSize, Math.max(pageSize, maxDisplayEntries), sortMode);

    if (!hasSearch) {
      if (sortMode == SortMode.CATEGORY) {
        appendEntriesWithCategoryPadding(
            appender, ordered, allowCategoryDupes, pageSize, displaySample);
      } else {
        appendEntries(appender, ordered, displaySample);
      }
      return appender.result(sortOrder, 0);
    }

    SearchQuery activeSearch = java.util.Objects.requireNonNull(searchQuery);
    List<StorageCache.StorageItem> matches = new ArrayList<>();
    List<StorageCache.StorageItem> others = new ArrayList<>();
    Map<String, List<String>> candidatesCache = new HashMap<>();
    PerfStats.measure(
        "gui.search",
        () -> {
          for (StorageCache.StorageItem item : ordered) {
            if (activeSearch.matchesCached(
                item, candidatesCache, itemNames, lang, keys, itemLanguage)) {
              matches.add(item);
            } else {
              others.add(item);
            }
          }
        });

    int searchResultsCount;
    if (matches.isEmpty()) {
      appender.appendPadding(pageSize);
      searchResultsCount = 0;
    } else {
      if (sortMode == SortMode.CATEGORY) {
        appendEntriesWithCategories(appender, matches, false, displaySample);
      } else {
        appendEntries(appender, matches, displaySample);
      }
      searchResultsCount = appender.totalSlots();
      appender.appendPadding((pageSize - (searchResultsCount % pageSize)) % pageSize);
    }
    if (sortMode == SortMode.CATEGORY) {
      appendEntriesWithCategoryPadding(appender, others, false, pageSize, displaySample);
    } else {
      appendEntries(appender, others, displaySample);
    }
    return appender.result(sortOrder, searchResultsCount);
  }

  private static void appendEntries(
      WindowAppender appender,
      List<StorageCache.StorageItem> items,
      Function<StorageCache.StorageItem, ItemStack> displaySample) {
    for (StorageCache.StorageItem item : items) {
      appender.appendItem(item, null, displaySample);
      if (appender.truncated()) {
        return;
      }
    }
  }

  private static void appendEntriesWithCategories(
      WindowAppender appender,
      List<StorageCache.StorageItem> items,
      boolean allowCategoryDupes,
      Function<StorageCache.StorageItem, ItemStack> displaySample) {
    CreativeTabOrder order = CreativeTabOrder.get();
    Map<String, Integer> seen = new HashMap<>();
    for (StorageCache.StorageItem item : items) {
      int category = categoryFor(item, order, seen, allowCategoryDupes);
      appender.appendItem(item, category, displaySample);
      if (appender.truncated()) {
        return;
      }
    }
  }

  private static void appendEntriesWithCategoryPadding(
      WindowAppender appender,
      List<StorageCache.StorageItem> items,
      boolean allowCategoryDupes,
      int pageSize,
      Function<StorageCache.StorageItem, ItemStack> displaySample) {
    if (items.isEmpty()) {
      return;
    }
    CreativeTabOrder order = CreativeTabOrder.get();
    Map<String, Integer> seen = new HashMap<>();
    int lastCategory = Integer.MIN_VALUE;
    for (StorageCache.StorageItem item : items) {
      int category = categoryFor(item, order, seen, allowCategoryDupes);
      if (lastCategory != Integer.MIN_VALUE && category != lastCategory) {
        appender.appendPadding((pageSize - (appender.totalSlots() % pageSize)) % pageSize);
      }
      lastCategory = category;
      appender.appendItem(item, category, displaySample);
      if (appender.truncated()) {
        return;
      }
    }
  }

  private static int categoryFor(
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

  record Result(
      List<DisplayEntry> displayList,
      List<Integer> displayCategories,
      List<String> sortOrder,
      int searchResultsCount,
      boolean truncated) {}

  private static final class WindowAppender {
    private final int windowStart;
    private final int windowEnd;
    private final int maxEntries;
    private final SortMode sortMode;
    private final List<DisplayEntry> visibleEntries = new ArrayList<>();
    private final List<Integer> visibleCategories = new ArrayList<>();
    private int totalSlots;
    private boolean truncated;

    private WindowAppender(int requestedPage, int pageSize, int maxEntries, SortMode sortMode) {
      int page = Math.max(0, requestedPage);
      this.windowStart = page * pageSize;
      this.windowEnd = windowStart + pageSize;
      this.maxEntries = maxEntries;
      this.sortMode = sortMode;
    }

    int totalSlots() {
      return totalSlots;
    }

    boolean truncated() {
      return truncated;
    }

    void appendPadding(int slots) {
      appendSlots(slots, null, null);
    }

    void appendItem(
        StorageCache.StorageItem item,
        Integer category,
        Function<StorageCache.StorageItem, ItemStack> displaySample) {
      if (item == null || item.amount() <= 0) {
        return;
      }
      ItemStack sample = displaySample.apply(item);
      if (sample == null) {
        return;
      }
      int maxStack = Math.max(1, sample.getMaxStackSize());
      long slotCountLong = Math.ceilDiv(item.amount(), (long) maxStack);
      int slotCount = clampSlotCount(slotCountLong);
      appendSlots(
          slotCount,
          globalIndex -> {
            long slotOffset = globalIndex - totalSlots;
            long remaining = item.amount() - slotOffset * maxStack;
            int amount = (int) Math.min(maxStack, Math.max(1L, remaining));
            return new DisplayEntry(item.key(), sample, amount);
          },
          category);
    }

    private int clampSlotCount(long slots) {
      if (slots <= 0L) {
        return 0;
      }
      long remainingLimit = maxEntries - (long) totalSlots;
      if (remainingLimit <= 0L) {
        truncated = true;
        return 0;
      }
      if (slots > remainingLimit) {
        truncated = true;
        return (int) Math.min(Integer.MAX_VALUE, remainingLimit);
      }
      return (int) Math.min(Integer.MAX_VALUE, slots);
    }

    private void appendSlots(int slots, EntryFactory factory, Integer category) {
      if (slots <= 0 || truncated && totalSlots >= maxEntries) {
        return;
      }
      int firstSlot = totalSlots;
      int lastSlotExclusive = (int) Math.min(Integer.MAX_VALUE, (long) totalSlots + slots);
      int visibleStart = Math.max(firstSlot, windowStart);
      int visibleEnd = Math.min(lastSlotExclusive, windowEnd);
      for (int globalIndex = visibleStart; globalIndex < visibleEnd; globalIndex++) {
        visibleEntries.add(factory == null ? null : factory.create(globalIndex));
        if (sortMode == SortMode.CATEGORY) {
          visibleCategories.add(category);
        }
      }
      totalSlots = lastSlotExclusive;
    }

    Result result(List<String> sortOrder, int searchResultsCount) {
      int size = Math.max(totalSlots, windowStart + visibleEntries.size());
      return new Result(
          new WindowedList<>(size, windowStart, visibleEntries),
          sortMode == SortMode.CATEGORY
              ? new WindowedList<>(size, windowStart, visibleCategories)
              : List.of(),
          sortOrder,
          searchResultsCount,
          truncated);
    }
  }

  private interface EntryFactory {
    DisplayEntry create(int globalIndex);
  }

  private static final class WindowedList<T> extends AbstractList<T> {
    private final int size;
    private final int windowStart;
    private final List<T> window;

    private WindowedList(int size, int windowStart, List<T> window) {
      this.size = Math.max(0, size);
      this.windowStart = Math.max(0, windowStart);
      this.window = Collections.unmodifiableList(new ArrayList<>(window));
    }

    @Override
    public T get(int index) {
      if (index < 0 || index >= size) {
        throw new IndexOutOfBoundsException(index);
      }
      int local = index - windowStart;
      if (local < 0 || local >= window.size()) {
        return null;
      }
      return window.get(local);
    }

    @Override
    public int size() {
      return size;
    }
  }
}
