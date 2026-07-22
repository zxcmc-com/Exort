package com.zxcmc.exort.storage;

import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.db.DbItem;
import com.zxcmc.exort.infra.scheduler.MainThreadWorkScheduler;
import com.zxcmc.exort.infra.scheduler.RoundRobinMainThreadScheduler;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.sort.SortMode;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public class StorageManager {
  private static final long FLUSH_WAIT_TIMEOUT_SECONDS = 15L;

  private final Database database;
  private final StorageKeys keys;
  private final Logger logger;
  private final Supplier<CacheDebugService> cacheDebugService;
  private final Supplier<CustomItems> customItems;
  private final Supplier<WirelessTerminalService> wirelessService;
  private final StorageFlushService flushService;
  private final StorageSortService sortService;
  private final MainThreadWorkScheduler hydrationScheduler;
  private final Map<String, StorageCache> caches = new ConcurrentHashMap<>();
  // Tracks in-flight loads to prevent duplicate DB work for the same storage id.
  private final Map<String, LoadOperation> loading = new ConcurrentHashMap<>();
  private final AtomicLong loadGenerationSequence = new AtomicLong();

  public StorageManager(
      Database database,
      Plugin schedulerPlugin,
      StorageKeys keys,
      Logger logger,
      Supplier<CacheDebugService> cacheDebugService,
      Supplier<String> defaultSortModeName,
      BiConsumer<String, String> persistSortMode,
      Supplier<CustomItems> customItems,
      Supplier<WirelessTerminalService> wirelessService,
      Supplier<StorageRuntimeConfig> runtimeConfig) {
    this(
        database,
        schedulerPlugin,
        keys,
        logger,
        cacheDebugService,
        defaultSortModeName,
        persistSortMode,
        customItems,
        wirelessService,
        runtimeConfig,
        null);
  }

  StorageManager(
      Database database,
      Plugin schedulerPlugin,
      StorageKeys keys,
      Logger logger,
      Supplier<CacheDebugService> cacheDebugService,
      Supplier<String> defaultSortModeName,
      BiConsumer<String, String> persistSortMode,
      Supplier<CustomItems> customItems,
      Supplier<WirelessTerminalService> wirelessService,
      Supplier<StorageRuntimeConfig> runtimeConfig,
      MainThreadWorkScheduler hydrationScheduler) {
    this.database = Objects.requireNonNull(database, "database");
    this.keys = Objects.requireNonNull(keys, "keys");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.cacheDebugService = Objects.requireNonNull(cacheDebugService, "cacheDebugService");
    this.customItems = Objects.requireNonNull(customItems, "customItems");
    this.wirelessService = Objects.requireNonNull(wirelessService, "wirelessService");
    this.flushService = new StorageFlushService(this.logger, this.cacheDebugService, this.database);
    this.sortService = new StorageSortService(defaultSortModeName, persistSortMode);
    this.hydrationScheduler =
        hydrationScheduler == null
            ? new RoundRobinMainThreadScheduler(
                Objects.requireNonNull(schedulerPlugin, "schedulerPlugin"),
                () -> {
                  StorageRuntimeConfig config = runtimeConfig.get();
                  return new RoundRobinMainThreadScheduler.Budget(
                      config.loadEntriesPerTick(), config.loadBudgetMicros());
                },
                "storage.hydration")
            : hydrationScheduler;
  }

  public CompletableFuture<StorageCache> getOrLoad(String storageId) {
    StorageCache cache = caches.computeIfAbsent(storageId, this::createCache);
    if (cache.isLoaded()) {
      cache.touch();
      return CompletableFuture.completedFuture(cache);
    }
    LoadOperation operation = new LoadOperation(loadGenerationSequence.incrementAndGet());
    LoadOperation existingLoad = loading.putIfAbsent(storageId, operation);
    if (existingLoad != null) {
      return existingLoad.result;
    }
    if (cache.isLoaded()) {
      cache.touch();
      operation.result.complete(cache);
      loading.remove(storageId, operation);
      operation.completion.complete(null);
      return operation.result;
    }
    database
        .ensureStorage(storageId)
        .thenCompose(
            v ->
                database
                    .loadStorageWithHealth(storageId)
                    .thenCombine(
                        database.getStorageSortMode(storageId),
                        (storage, sortMode) ->
                            new LoadedStorageData(storage, sortMode, Optional.empty()))
                    .thenCombine(
                        database.getStorageDisplayName(storageId),
                        (data, displayName) ->
                            new LoadedStorageData(data.storage(), data.sortMode(), displayName)))
        .thenCompose(data -> finishLoadOnMainThread(storageId, cache, data, operation))
        .whenComplete(
            (res, err) -> {
              try {
                if (err != null) {
                  operation.result.completeExceptionally(err);
                } else if (!isCurrentLoad(storageId, operation)) {
                  operation.result.completeExceptionally(staleLoad(storageId, operation));
                } else {
                  operation.result.complete(res);
                }
              } finally {
                loading.remove(storageId, operation);
                operation.completion.complete(null);
              }
            });
    return operation.result;
  }

  private CompletableFuture<StorageCache> finishLoadOnMainThread(
      String storageId, StorageCache cache, LoadedStorageData data, LoadOperation operation) {
    StorageCache staging = createCache(storageId);
    return hydrationScheduler
        .submit(new HydrationWork(storageId, staging, data, operation))
        .thenCompose(
            installed -> {
              requireCurrentLoad(storageId, operation);
              return database
                  .quarantineStorageItems(storageId, installed.quarantineEntries())
                  .thenApply(
                      ignored -> {
                        requireCurrentLoad(storageId, operation);
                        if (!caches.replace(storageId, cache, installed.cache())) {
                          throw staleLoad(storageId, operation);
                        }
                        return installed.cache();
                      });
            })
        .whenComplete(
            (loadedCache, error) -> {
              if (error != null) {
                caches.remove(storageId, cache);
              }
            });
  }

  public void flushDirtyCaches() {
    flushService.flushBatchAsync(caches.values());
  }

  public void flush(StorageCache cache) {
    flushService.flushAsync(cache);
  }

  public void flushAllAndWait() {
    flushAllAndWait(FLUSH_WAIT_TIMEOUT_SECONDS);
  }

  public void flushAllAndWait(long timeoutSeconds) {
    CompletableFuture<?>[] futures =
        new CompletableFuture<?>[] {flushService.flushBatchAsync(caches.values())};
    if (futures.length == 0) return;
    try {
      CompletableFuture.allOf(futures).get(Math.max(1L, timeoutSeconds), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      logger.log(
          Level.SEVERE,
          "Timed out waiting for "
              + futures.length
              + " storage flush task(s); dirty caches may remain pending",
          e);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error waiting for storage flush", e);
    }
  }

  public Optional<StorageCache> getLoadedCache(String storageId) {
    StorageCache cache = caches.get(storageId);
    if (cache == null || !cache.isLoaded()) {
      return Optional.empty();
    }
    cache.touch();
    return Optional.of(cache);
  }

  public Optional<StorageCache> peekLoadedCache(String storageId) {
    StorageCache cache = caches.get(storageId);
    if (cache == null || !cache.isLoaded()) {
      return Optional.empty();
    }
    return Optional.of(cache);
  }

  public Optional<StorageCache> getCache(String storageId) {
    StorageCache cache = caches.get(storageId);
    return Optional.ofNullable(cache);
  }

  public void setCachedDisplayName(String storageId, String displayName) {
    if (storageId == null) {
      return;
    }
    StorageCache cache = caches.get(storageId);
    if (cache != null) {
      cache.setDisplayName(displayName);
    }
  }

  public CompletableFuture<Void> discardCacheForInternalCleanup(String storageId) {
    if (storageId == null) return CompletableFuture.completedFuture(null);
    LoadOperation operation = loading.remove(storageId);
    caches.remove(storageId);
    if (operation == null) {
      return CompletableFuture.completedFuture(null);
    }
    operation.result.completeExceptionally(staleLoad(storageId, operation));
    return operation.completion;
  }

  public void shutdown() {
    hydrationScheduler.close();
  }

  public int refreshLoadedCustomItems() {
    int refreshed = 0;
    for (StorageCache cache : caches.values()) {
      if (cache == null || !cache.isLoaded()) continue;
      refreshed += refreshCustomItems(cache);
    }
    return refreshed;
  }

  public boolean isLoading(String storageId) {
    if (storageId == null) return false;
    return loading.containsKey(storageId);
  }

  private boolean isCurrentLoad(String storageId, LoadOperation operation) {
    return loading.get(storageId) == operation;
  }

  private void requireCurrentLoad(String storageId, LoadOperation operation) {
    if (!isCurrentLoad(storageId, operation)) {
      throw staleLoad(storageId, operation);
    }
  }

  private static CancellationException staleLoad(String storageId, LoadOperation operation) {
    return new CancellationException(
        "Storage load generation " + operation.generation + " was invalidated for " + storageId);
  }

  public CompletableFuture<Void> cloneStorage(String fromId, String toId, String tierKey) {
    return cloneStorage(fromId, toId, tierKey, null);
  }

  public CompletableFuture<Void> cloneStorage(
      String fromId, String toId, String tierKey, Long tierMaxItems) {
    if (fromId == null || toId == null) {
      return CompletableFuture.completedFuture(null);
    }
    Optional<StorageCache> loaded = peekLoadedCache(fromId);
    if (loaded.isPresent()) {
      return snapshotLoadedCacheForClone(loaded.get(), toId)
          .thenCompose(
              snapshot ->
                  database
                      .createStorageWithItems(
                          toId,
                          tierKey,
                          tierMaxItems,
                          snapshot.sortMode().name(),
                          snapshot.displayName(),
                          snapshot.items())
                      .thenApply(ignored -> null));
    }
    return database.cloneStorage(fromId, toId, tierKey, tierMaxItems);
  }

  private CompletableFuture<CloneSnapshot> snapshotLoadedCacheForClone(
      StorageCache cache, String toId) {
    return hydrationScheduler.submit(new CloneSnapshotWork(cache, toId));
  }

  public int evictIdleCaches(long idleMs) {
    if (idleMs <= 0) return 0;
    long now = System.currentTimeMillis();
    int evicted = 0;
    for (var entry : caches.entrySet()) {
      String storageId = entry.getKey();
      StorageCache cache = entry.getValue();
      if (cache == null || !cache.isLoaded()) continue;
      if (cache.isDirty() || cache.hasViewers()) continue;
      if (loading.containsKey(storageId)) continue;
      long idle = now - cache.lastAccessMs();
      if (idle < idleMs) continue;
      if (caches.remove(storageId, cache)) {
        evicted++;
        log(
            CacheDebugService.EventType.EVICT,
            storageId,
            "cache evict: " + storageId + " idleMs=" + idle + " viewers=" + cache.hasViewers(),
            idle);
      }
    }
    return evicted;
  }

  private void log(
      CacheDebugService.EventType type, String storageId, String message, long amount) {
    var debug = cacheDebugService.get();
    if (debug != null && debug.isEnabled()) {
      debug.record(type, storageId, message, amount);
    }
  }

  private record LoadedStorageData(
      StorageLoadResult storage, Optional<String> sortMode, Optional<String> displayName) {}

  private record InstalledCache(
      StorageCache cache, Collection<StorageQuarantineEntry> quarantineEntries) {}

  private final class HydrationWork implements RoundRobinMainThreadScheduler.Work<InstalledCache> {
    private final String storageId;
    private final StorageCache staging;
    private final LoadedStorageData data;
    private final LoadOperation operation;
    private final Iterator<DbItem> items;
    private StorageCache.IncrementalLoad incrementalLoad;
    private StorageCache.LoadResult loadResult;
    private StorageCache.IncrementalCustomItemRefresh customRefresh;
    private boolean customRefreshFinished;
    private CustomItems refreshCustomItems;
    private WirelessTerminalService refreshWirelessService;
    private InstalledCache result;

    private HydrationWork(
        String storageId, StorageCache staging, LoadedStorageData data, LoadOperation operation) {
      this.storageId = storageId;
      this.staging = staging;
      this.data = data;
      this.operation = operation;
      this.items = data.storage().items().values().iterator();
    }

    @Override
    public RoundRobinMainThreadScheduler.Slice runSlice(int maxEntries, long deadlineNanos) {
      requireCurrentLoad(storageId, operation);
      if (incrementalLoad == null) {
        incrementalLoad = staging.beginIncrementalLoad(data.storage().structuralCorruptions());
      }
      int processed = 0;
      while (processed < maxEntries && items.hasNext() && System.nanoTime() < deadlineNanos) {
        requireCurrentLoad(storageId, operation);
        staging.appendIncrementalLoad(incrementalLoad, items.next());
        processed++;
      }
      if (items.hasNext()) {
        return new RoundRobinMainThreadScheduler.Slice(processed, false);
      }
      if (loadResult == null) {
        loadResult = staging.finishIncrementalLoad(incrementalLoad);
        staging.setDisplayName(data.displayName().orElse(null));
        customRefresh = staging.beginIncrementalCustomItemRefresh();
        refreshCustomItems = customItems.get();
        refreshWirelessService = wirelessService.get();
      }
      while (processed < maxEntries && System.nanoTime() < deadlineNanos) {
        requireCurrentLoad(storageId, operation);
        boolean hasMore =
            staging.refreshNextCustomItem(
                customRefresh, refreshCustomItems, refreshWirelessService, true);
        processed++;
        if (!hasMore) {
          staging.finishIncrementalCustomItemRefresh(customRefresh);
          customRefreshFinished = true;
          break;
        }
      }
      if (customRefresh == null || result != null) {
        throw new IllegalStateException("Invalid Storage hydration refresh state");
      }
      StorageCache.IndexCursor refreshedCursor = staging.beginIndexCursor();
      if (!customRefreshFinished && customRefresh.offset() >= refreshedCursor.size()) {
        staging.finishIncrementalCustomItemRefresh(customRefresh);
        customRefreshFinished = true;
      }
      if (!customRefreshFinished) {
        return new RoundRobinMainThreadScheduler.Slice(Math.max(1, processed), false);
      }
      SortMode mode = sortService.resolveAndPersistDefault(storageId, data.sortMode());
      staging.setSortMode(mode);
      requireCurrentLoad(storageId, operation);
      result = new InstalledCache(staging, loadResult.quarantineEntries());
      return new RoundRobinMainThreadScheduler.Slice(Math.max(1, processed), true);
    }

    @Override
    public InstalledCache result() {
      return result;
    }
  }

  private static final class LoadOperation {
    private final long generation;
    private final CompletableFuture<StorageCache> result = new CompletableFuture<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    private LoadOperation(long generation) {
      this.generation = generation;
    }
  }

  private record CloneSnapshot(Collection<DbItem> items, SortMode sortMode, String displayName) {}

  private static final class CloneSnapshotWork
      implements RoundRobinMainThreadScheduler.Work<CloneSnapshot> {
    private final StorageCache cache;
    private final String targetStorageId;
    private final List<DbItem> items = new java.util.ArrayList<>();
    private StorageCache.IndexCursor cursor;
    private long expectedContentVersion;
    private int offset;
    private CloneSnapshot result;

    private CloneSnapshotWork(StorageCache cache, String targetStorageId) {
      this.cache = cache;
      this.targetStorageId = targetStorageId;
    }

    @Override
    public RoundRobinMainThreadScheduler.Slice runSlice(int maxEntries, long deadlineNanos) {
      if (cursor == null) {
        cursor = cache.beginIndexCursor();
        expectedContentVersion = cursor.contentVersion();
      }
      StorageCache.DbItemIndexBatch batch = cache.readDbItemIndexBatch(cursor, offset, maxEntries);
      if (!batch.valid()) {
        throw new CancellationException(
            "Source storage changed structurally while cloning to " + targetStorageId);
      }
      items.addAll(batch.items());
      int previousOffset = offset;
      offset = batch.nextOffset();
      cursor = batch.current();
      boolean complete = offset >= cursor.size();
      if (complete) {
        StorageCache.IndexCursor current = cache.beginIndexCursor();
        if (current.structuralVersion() != cursor.structuralVersion()
            || current.contentVersion() != expectedContentVersion) {
          items.clear();
          cursor = current;
          expectedContentVersion = current.contentVersion();
          offset = 0;
          return new RoundRobinMainThreadScheduler.Slice(Math.max(1, batch.items().size()), false);
        }
        result = new CloneSnapshot(List.copyOf(items), cache.getSortMode(), cache.getDisplayName());
      }
      return new RoundRobinMainThreadScheduler.Slice(
          Math.max(1, offset - previousOffset), complete);
    }

    @Override
    public CloneSnapshot result() {
      return result;
    }
  }

  private StorageCache createCache(String storageId) {
    return new StorageCache(storageId, keys, logger, cacheDebugService);
  }

  private int refreshCustomItems(StorageCache cache) {
    return cache.refreshCustomItems(customItems.get(), wirelessService.get(), true);
  }
}
