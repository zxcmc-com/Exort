package com.zxcmc.exort.gui;

import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.scheduler.MainThreadWorkScheduler;
import com.zxcmc.exort.infra.scheduler.RoundRobinMainThreadScheduler;
import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.storage.sort.SortMode;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Builds shared storage snapshots and viewer-specific display indexes under a global tick budget.
 */
public final class StorageDisplayIndexService implements AutoCloseable {
  private final MainThreadWorkScheduler scheduler;
  private final Map<String, ActiveIndex> active = new HashMap<>();
  private long generationSequence;

  public StorageDisplayIndexService(Plugin plugin, Supplier<GuiRuntimeConfig> configSource) {
    scheduler =
        new RoundRobinMainThreadScheduler(
            plugin,
            () -> {
              GuiRuntimeConfig config = configSource.get();
              return new RoundRobinMainThreadScheduler.Budget(
                  config.indexEntriesPerTick(), config.indexBudgetMicros());
            },
            "gui.index");
  }

  StorageDisplayIndexService(MainThreadWorkScheduler scheduler) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
  }

  public void acquire(StorageCache cache) {
    Objects.requireNonNull(cache, "cache");
    ActiveIndex state =
        active.computeIfAbsent(
            cache.getStorageId(), ignored -> new ActiveIndex(cache, ++generationSequence));
    state.viewers++;
  }

  public void release(StorageCache cache) {
    if (cache == null) {
      return;
    }
    ActiveIndex state = active.get(cache.getStorageId());
    if (state == null || state.cache != cache) {
      return;
    }
    state.viewers = Math.max(0, state.viewers - 1);
    if (state.viewers == 0) {
      active.remove(cache.getStorageId(), state);
      state.generation++;
      state.entries = List.of();
      state.baseReady = false;
      state.build = null;
      cancelPending(state);
    }
  }

  public CompletableFuture<Result> request(Request request) {
    Objects.requireNonNull(request, "request");
    ActiveIndex state = active.get(request.cache().getStorageId());
    if (state == null || state.cache != request.cache() || state.viewers <= 0) {
      return CompletableFuture.failedFuture(
          new CancellationException("Storage index has no active viewers"));
    }
    long generation = state.generation;
    CompletableFuture<Result> requested = new CompletableFuture<>();
    CompletableFuture<Result> previous = state.pendingByOwner.put(request.owner(), requested);
    if (previous != null) {
      previous.cancel(false);
    }
    AtomicReference<CompletableFuture<Result>> scheduledWork = new AtomicReference<>();
    ensureBaseIndex(state)
        .whenComplete(
            (entries, baseFailure) -> {
              if (requested.isDone()) {
                return;
              }
              if (baseFailure != null) {
                requested.completeExceptionally(baseFailure);
                return;
              }
              try {
                requireActive(state, generation);
                CompletableFuture<Result> work =
                    scheduler.submit(new DisplayBuildWork(state, generation, entries, request));
                scheduledWork.set(work);
                if (requested.isCancelled()) {
                  work.cancel(false);
                  return;
                }
                work.whenComplete(
                    (result, failure) -> {
                      if (failure != null) {
                        requested.completeExceptionally(failure);
                        return;
                      }
                      try {
                        requireActive(state, generation);
                        StorageCache.IndexCursor current = state.cache.beginIndexCursor();
                        if (current.structuralVersion() != result.structuralVersion()
                            || current.contentVersion() != result.contentVersion()) {
                          throw new StaleStructureException(current, result);
                        }
                        requested.complete(result);
                      } catch (RuntimeException | LinkageError validationFailure) {
                        requested.completeExceptionally(validationFailure);
                      }
                    });
              } catch (RuntimeException | LinkageError submissionFailure) {
                requested.completeExceptionally(submissionFailure);
              }
            });
    requested.whenComplete(
        (ignored, failure) -> {
          state.pendingByOwner.remove(request.owner(), requested);
          if (requested.isCancelled()) {
            CompletableFuture<Result> work = scheduledWork.get();
            if (work != null) {
              work.cancel(false);
            }
          }
        });
    return requested;
  }

  private CompletableFuture<List<StorageCache.StorageItem>> ensureBaseIndex(ActiveIndex state) {
    StorageCache.IndexCursor current = state.cache.beginIndexCursor();
    if (state.baseReady && state.structuralVersion == current.structuralVersion()) {
      state.contentVersion = current.contentVersion();
      return CompletableFuture.completedFuture(state.entries);
    }
    if (state.build != null && !state.build.isDone()) {
      return state.build;
    }
    long generation = state.generation;
    CompletableFuture<List<StorageCache.StorageItem>> scheduled =
        scheduler.submit(new BaseBuildWork(state, generation));
    CompletableFuture<List<StorageCache.StorageItem>> build = new CompletableFuture<>();
    state.build = build;
    scheduled.whenComplete(
        (entries, failure) -> {
          if (failure == null && isActive(state, generation)) {
            StorageCache.IndexCursor cursor = state.cache.beginIndexCursor();
            state.entries = List.copyOf(entries);
            state.structuralVersion = cursor.structuralVersion();
            state.contentVersion = cursor.contentVersion();
            state.baseReady = true;
            build.complete(state.entries);
          } else if (failure != null) {
            build.completeExceptionally(failure);
          } else {
            build.completeExceptionally(
                new CancellationException("Storage base index generation was closed"));
          }
          if (state.build == build) {
            state.build = null;
          }
        });
    return build;
  }

  private boolean isActive(ActiveIndex state, long generation) {
    return state.viewers > 0
        && state.generation == generation
        && active.get(state.cache.getStorageId()) == state;
  }

  private void requireActive(ActiveIndex state, long generation) {
    if (!isActive(state, generation)) {
      throw new CancellationException("Storage display index generation was closed");
    }
  }

  @Override
  public void close() {
    for (ActiveIndex state : active.values()) {
      state.generation++;
      state.viewers = 0;
      state.entries = List.of();
      state.baseReady = false;
      cancelPending(state);
    }
    active.clear();
    scheduler.close();
  }

  int activeIndexCount() {
    return active.size();
  }

  int pendingRequestCount() {
    return active.values().stream().mapToInt(state -> state.pendingByOwner.size()).sum();
  }

  private static void cancelPending(ActiveIndex state) {
    for (CompletableFuture<Result> pending : List.copyOf(state.pendingByOwner.values())) {
      pending.cancel(false);
    }
    state.pendingByOwner.clear();
  }

  public record Request(
      Object owner,
      StorageCache cache,
      SortMode sortMode,
      boolean sortFrozen,
      List<String> previousSortOrder,
      SearchQuery searchQuery,
      ItemNameService itemNames,
      Lang lang,
      StorageKeys keys,
      StorageTierCatalog storageTiers,
      String language,
      int page,
      int pageSize,
      Function<StorageCache.StorageItem, ItemStack> displaySample) {
    public Request {
      Objects.requireNonNull(owner, "owner");
      Objects.requireNonNull(cache, "cache");
      Objects.requireNonNull(sortMode, "sortMode");
      previousSortOrder = List.copyOf(previousSortOrder);
      Objects.requireNonNull(searchQuery, "searchQuery");
      Objects.requireNonNull(storageTiers, "storageTiers");
      Objects.requireNonNull(language, "language");
      if (pageSize <= 0) {
        throw new IllegalArgumentException("pageSize must be positive");
      }
      Objects.requireNonNull(displaySample, "displaySample");
    }
  }

  public record Result(
      List<DisplayEntry> displayList,
      List<Integer> displayCategories,
      List<String> sortOrder,
      int searchResultsCount,
      long cacheVersion,
      long structuralVersion,
      long contentVersion) {}

  public static final class StaleStructureException extends RuntimeException {
    private StaleStructureException() {
      super("Storage structure or content changed while the display index was being built");
    }

    private StaleStructureException(IndexCursorView current, IndexCursorView result) {
      super(
          "Storage structure or content changed while the display index was being built: current="
              + current
              + ", result="
              + result);
    }

    private StaleStructureException(
        StorageCache.IndexCursor current, StorageDisplayIndexService.Result result) {
      this(
          new IndexCursorView(current.structuralVersion(), current.contentVersion()),
          new IndexCursorView(result.structuralVersion(), result.contentVersion()));
    }
  }

  private record IndexCursorView(long structuralVersion, long contentVersion) {}

  private static final class ActiveIndex {
    private final StorageCache cache;
    private long generation;
    private int viewers;
    private long structuralVersion = -1L;
    private long contentVersion = -1L;
    private List<StorageCache.StorageItem> entries = List.of();
    private boolean baseReady;
    private CompletableFuture<List<StorageCache.StorageItem>> build;
    private final Map<Object, CompletableFuture<Result>> pendingByOwner = new HashMap<>();

    private ActiveIndex(StorageCache cache, long generation) {
      this.cache = cache;
      this.generation = generation;
    }
  }

  private final class BaseBuildWork
      implements RoundRobinMainThreadScheduler.Work<List<StorageCache.StorageItem>> {
    private final ActiveIndex state;
    private final long generation;
    private final List<StorageCache.StorageItem> entries = new ArrayList<>();
    private StorageCache.IndexCursor cursor;
    private int offset;

    private BaseBuildWork(ActiveIndex state, long generation) {
      this.state = state;
      this.generation = generation;
    }

    @Override
    public RoundRobinMainThreadScheduler.Slice runSlice(int maxEntries, long deadlineNanos) {
      requireActive(state, generation);
      if (cursor == null) {
        cursor = state.cache.beginIndexCursor();
      }
      StorageCache.IndexBatch batch = state.cache.readIndexBatch(cursor, offset, maxEntries);
      if (!batch.valid()) {
        entries.clear();
        cursor = state.cache.beginIndexCursor();
        offset = 0;
        return new RoundRobinMainThreadScheduler.Slice(1, false);
      }
      entries.addAll(batch.items());
      offset = batch.nextOffset();
      boolean complete = offset >= batch.current().size();
      cursor = batch.current();
      return new RoundRobinMainThreadScheduler.Slice(Math.max(1, batch.items().size()), complete);
    }

    @Override
    public List<StorageCache.StorageItem> result() {
      return entries;
    }
  }

  private final class DisplayBuildWork implements RoundRobinMainThreadScheduler.Work<Result> {
    private enum Phase {
      PREPARE,
      SORT,
      ASSEMBLE,
      COMPLETE
    }

    private final ActiveIndex state;
    private final long generation;
    private final List<StorageCache.StorageItem> entries;
    private final Request request;
    private final List<Metadata> metadata = new ArrayList<>();
    private final Map<String, List<String>> candidatesCache = new HashMap<>();
    private final Map<String, Integer> frozenRanks = new HashMap<>();
    private final StorageCache.IndexCursor sourceVersion;
    private final long sourceCacheVersion;
    private Phase phase = Phase.PREPARE;
    private int cursor;
    private IncrementalMergeSort sorter;
    private Assembly assembly;
    private Result result;

    private DisplayBuildWork(
        ActiveIndex state,
        long generation,
        List<StorageCache.StorageItem> entries,
        Request request) {
      this.state = state;
      this.generation = generation;
      this.entries = entries;
      this.request = request;
      this.sourceVersion =
          new StorageCache.IndexCursor(
              state.structuralVersion, state.contentVersion, state.entries.size());
      this.sourceCacheVersion = state.cache.version();
      for (int i = 0; i < request.previousSortOrder().size(); i++) {
        frozenRanks.putIfAbsent(request.previousSortOrder().get(i), i);
      }
    }

    @Override
    public RoundRobinMainThreadScheduler.Slice runSlice(int maxEntries, long deadlineNanos) {
      requireActive(state, generation);
      long sliceStarted = System.nanoTime();
      long sortDeadlineNanos =
          sliceStarted + Math.max(1L, Math.max(0L, deadlineNanos - sliceStarted) / 4L);
      int processed = 0;
      boolean madeProgress = false;
      while (processed < maxEntries && System.nanoTime() < deadlineNanos) {
        switch (phase) {
          case PREPARE -> {
            if (cursor >= entries.size()) {
              sorter = new IncrementalMergeSort(metadata, metadataComparator());
              phase = Phase.SORT;
              continue;
            }
            prepare(entries.get(cursor++));
            processed++;
            madeProgress = true;
          }
          case SORT -> {
            // Sorting is CPU-dense compared with preparing or assembling one entry. Reserve only
            // part of the shared time slice so faster convergence does not turn into an MSPT
            // regression for smaller indexes.
            if (System.nanoTime() >= sortDeadlineNanos) {
              return new RoundRobinMainThreadScheduler.Slice(processed, false, madeProgress);
            }
            if (sorter.step()) {
              assembly = new Assembly(sorter.result(), request);
              phase = Phase.ASSEMBLE;
              madeProgress = true;
              continue;
            }
            // Comparator work is bounded by the time deadline, but it is not another storage
            // entry. Charging every merge step against entriesPerTick turns an O(n log n) sort
            // into thousands of mostly idle ticks for large storages.
            madeProgress = true;
          }
          case ASSEMBLE -> {
            if (assembly.step()) {
              result =
                  assembly.result(
                      sourceCacheVersion,
                      sourceVersion.structuralVersion(),
                      sourceVersion.contentVersion());
              phase = Phase.COMPLETE;
              continue;
            }
            processed++;
            madeProgress = true;
          }
          case COMPLETE -> {
            return new RoundRobinMainThreadScheduler.Slice(processed, true, true);
          }
        }
      }
      return new RoundRobinMainThreadScheduler.Slice(
          processed, phase == Phase.COMPLETE, madeProgress);
    }

    private void prepare(StorageCache.StorageItem item) {
      item = state.cache.readIndexItem(item.key()).orElse(null);
      if (item == null) {
        return;
      }
      String name =
          SortSearchHelper.sortNameKey(
              item.sample(),
              request.itemNames(),
              request.lang(),
              request.keys(),
              request.storageTiers(),
              request.language());
      String id = item.sample().getType().getKey().getKey();
      boolean match =
          request
              .searchQuery()
              .matchesCached(
                  item,
                  candidatesCache,
                  request.itemNames(),
                  request.lang(),
                  request.keys(),
                  request.storageTiers(),
                  request.language());
      CreativeTabOrder order = CreativeTabOrder.get();
      if (request.sortMode() == SortMode.CATEGORY
          && request.searchQuery().isEmpty()
          && order != null) {
        List<CreativeTabOrder.Position> positions = order.positionsFor(item.sample());
        if (positions != null && !positions.isEmpty()) {
          for (CreativeTabOrder.Position position : positions) {
            metadata.add(new Metadata(item, name, id, match, position));
          }
          return;
        }
      }
      CreativeTabOrder.Position position =
          order == null
              ? new CreativeTabOrder.Position(SortSearchHelper.categoryIndex(item.sample()), 0)
              : order.positionFor(item.sample());
      metadata.add(new Metadata(item, name, id, match, position));
    }

    private Comparator<Metadata> metadataComparator() {
      Comparator<Metadata> normal =
          switch (request.sortMode()) {
            case AMOUNT ->
                Comparator.comparingLong((Metadata value) -> value.item.amount())
                    .reversed()
                    .thenComparing(value -> value.item.sample().getType().name())
                    .thenComparing(value -> value.name, String.CASE_INSENSITIVE_ORDER);
            case NAME ->
                Comparator.comparing((Metadata value) -> value.name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(value -> value.id, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(
                        Comparator.comparingLong((Metadata value) -> value.item.amount())
                            .reversed());
            case ID ->
                Comparator.comparing((Metadata value) -> value.id, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(
                        Comparator.comparingLong((Metadata value) -> value.item.amount())
                            .reversed());
            case CATEGORY ->
                Comparator.comparingInt((Metadata value) -> value.position.tabIndex())
                    .thenComparingInt(value -> value.position.indexInTab())
                    .thenComparing(value -> value.name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(
                        Comparator.comparingLong((Metadata value) -> value.item.amount())
                            .reversed());
          };
      if (request.sortFrozen() && !frozenRanks.isEmpty()) {
        normal =
            Comparator.comparingInt(
                    (Metadata value) ->
                        frozenRanks.getOrDefault(value.item.key(), Integer.MAX_VALUE))
                .thenComparing(normal);
      }
      if (!request.searchQuery().isEmpty()) {
        normal = Comparator.comparing((Metadata value) -> !value.searchMatch).thenComparing(normal);
      }
      return normal;
    }

    @Override
    public Result result() {
      return result;
    }
  }

  private record Metadata(
      StorageCache.StorageItem item,
      String name,
      String id,
      boolean searchMatch,
      CreativeTabOrder.Position position) {}

  private static final class IncrementalMergeSort {
    private final Comparator<Metadata> comparator;
    private Object[] source;
    private Object[] target;
    private int width = 1;
    private int start;
    private int left;
    private int middle;
    private int right;
    private int i;
    private int j;
    private int output;
    private boolean merging;
    private boolean complete;

    private IncrementalMergeSort(List<Metadata> values, Comparator<Metadata> comparator) {
      this.comparator = comparator;
      source = values.toArray();
      target = new Object[source.length];
      complete = source.length <= 1;
    }

    private boolean step() {
      if (complete) {
        return true;
      }
      if (!merging) {
        if (start >= source.length) {
          Object[] swap = source;
          source = target;
          target = swap;
          width = Math.multiplyExact(width, 2);
          start = 0;
          if (width >= source.length) {
            complete = true;
            return true;
          }
        }
        left = start;
        middle = Math.min(source.length, start + width);
        right = Math.min(source.length, start + 2 * width);
        i = left;
        j = middle;
        output = left;
        merging = true;
      }
      if (i < middle && (j >= right || compare(i, j) <= 0)) {
        target[output++] = source[i++];
      } else if (j < right) {
        target[output++] = source[j++];
      }
      if (output >= right) {
        start = right;
        merging = false;
      }
      return false;
    }

    private int compare(int first, int second) {
      return comparator.compare((Metadata) source[first], (Metadata) source[second]);
    }

    private List<Metadata> result() {
      List<Metadata> result = new ArrayList<>(source.length);
      for (Object value : source) {
        result.add((Metadata) value);
      }
      return result;
    }
  }

  private static final class Assembly {
    private final List<Metadata> ordered;
    private final Request request;
    private final int windowStart;
    private final int windowEnd;
    private final List<DisplayEntry> visible = new ArrayList<>();
    private final List<Integer> categories = new ArrayList<>();
    private final List<String> sortOrder = new ArrayList<>();
    private int cursor;
    private int totalSlots;
    private int searchResultsCount;
    private int lastCategory = Integer.MIN_VALUE;
    private boolean searchBoundaryApplied;
    private boolean complete;

    private Assembly(List<Metadata> ordered, Request request) {
      this.ordered = ordered;
      this.request = request;
      this.windowStart = Math.max(0, request.page()) * request.pageSize();
      this.windowEnd = windowStart + request.pageSize();
    }

    private boolean step() {
      if (complete) {
        return true;
      }
      if (cursor >= ordered.size()) {
        if (!request.searchQuery().isEmpty() && !searchBoundaryApplied) {
          applySearchBoundary();
        }
        complete = true;
        return true;
      }
      Metadata metadata = ordered.get(cursor++);
      if (!request.searchQuery().isEmpty() && !metadata.searchMatch && !searchBoundaryApplied) {
        applySearchBoundary();
      }
      int category = metadata.position.tabIndex();
      boolean categoryPadding =
          request.sortMode() == SortMode.CATEGORY
              && (request.searchQuery().isEmpty() || searchBoundaryApplied);
      if (categoryPadding && lastCategory != Integer.MIN_VALUE && lastCategory != category) {
        appendPadding((request.pageSize() - totalSlots % request.pageSize()) % request.pageSize());
      }
      lastCategory = category;
      appendItem(metadata, category);
      sortOrder.add(metadata.item.key());
      return false;
    }

    private void applySearchBoundary() {
      searchResultsCount = totalSlots;
      if (searchResultsCount == 0) {
        appendPadding(request.pageSize());
      } else {
        appendPadding(
            (request.pageSize() - searchResultsCount % request.pageSize()) % request.pageSize());
      }
      searchBoundaryApplied = true;
      lastCategory = Integer.MIN_VALUE;
    }

    private void appendItem(Metadata metadata, int category) {
      int maxStack = Math.max(1, metadata.item.sample().getMaxStackSize());
      long slotsLong = Math.ceilDiv(metadata.item.amount(), (long) maxStack);
      int slots = (int) Math.min(Integer.MAX_VALUE, slotsLong);
      int first = totalSlots;
      int last = (int) Math.min(Integer.MAX_VALUE, (long) totalSlots + slots);
      int visibleStart = Math.max(first, windowStart);
      int visibleEnd = Math.min(last, windowEnd);
      if (visibleStart < visibleEnd) {
        StorageCache.StorageItem visibleItem =
            new StorageCache.StorageItem(
                metadata.item.key(),
                ItemKeyUtil.cloneSample(metadata.item.sample()),
                metadata.item.amount(),
                metadata.item.weight(),
                null);
        ItemStack sample = request.displaySample().apply(visibleItem);
        if (sample != null) {
          for (int slot = visibleStart; slot < visibleEnd; slot++) {
            long offset = slot - (long) first;
            long remaining = metadata.item.amount() - offset * maxStack;
            visible.add(
                new DisplayEntry(metadata.item.key(), sample, (int) Math.min(maxStack, remaining)));
            if (request.sortMode() == SortMode.CATEGORY) {
              categories.add(category);
            }
          }
        }
      }
      totalSlots = last;
    }

    private void appendPadding(int slots) {
      if (slots <= 0) {
        return;
      }
      int first = totalSlots;
      int last = (int) Math.min(Integer.MAX_VALUE, (long) totalSlots + slots);
      int visibleStart = Math.max(first, windowStart);
      int visibleEnd = Math.min(last, windowEnd);
      for (int slot = visibleStart; slot < visibleEnd; slot++) {
        visible.add(null);
        if (request.sortMode() == SortMode.CATEGORY) {
          categories.add(null);
        }
      }
      totalSlots = last;
    }

    private Result result(long cacheVersion, long structuralVersion, long contentVersion) {
      return new Result(
          new WindowedList<>(totalSlots, windowStart, visible),
          request.sortMode() == SortMode.CATEGORY
              ? new WindowedList<>(totalSlots, windowStart, categories)
              : List.of(),
          List.copyOf(sortOrder),
          searchResultsCount,
          cacheVersion,
          structuralVersion,
          contentVersion);
    }
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
      return local >= 0 && local < window.size() ? window.get(local) : null;
    }

    @Override
    public int size() {
      return size;
    }
  }
}
