package com.zxcmc.exort.storage;

import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.gui.SortMode;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.db.DbItem;
import com.zxcmc.exort.keys.StorageKeys;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class StorageManager {
  private static final long FLUSH_WAIT_TIMEOUT_SECONDS = 15L;

  private final Database database;
  private final Plugin schedulerPlugin;
  private final BooleanSupplier pluginEnabled;
  private final StorageKeys keys;
  private final Logger logger;
  private final Supplier<CacheDebugService> cacheDebugService;
  private final ToIntFunction<StorageCache> customItemRefresher;
  private final StorageFlushService flushService;
  private final StorageSortService sortService;
  private final Map<String, StorageCache> caches = new ConcurrentHashMap<>();
  // Tracks in-flight loads to prevent duplicate DB work for the same storage id.
  private final Map<String, CompletableFuture<StorageCache>> loading = new ConcurrentHashMap<>();

  public StorageManager(
      Database database,
      Plugin schedulerPlugin,
      BooleanSupplier pluginEnabled,
      StorageKeys keys,
      Logger logger,
      Supplier<CacheDebugService> cacheDebugService,
      Supplier<String> defaultSortModeName,
      BiConsumer<String, String> persistSortMode,
      ToIntFunction<StorageCache> customItemRefresher) {
    this.database = Objects.requireNonNull(database, "database");
    this.schedulerPlugin = Objects.requireNonNull(schedulerPlugin, "schedulerPlugin");
    this.pluginEnabled = Objects.requireNonNull(pluginEnabled, "pluginEnabled");
    this.keys = Objects.requireNonNull(keys, "keys");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.cacheDebugService = Objects.requireNonNull(cacheDebugService, "cacheDebugService");
    this.customItemRefresher = Objects.requireNonNull(customItemRefresher, "customItemRefresher");
    this.flushService = new StorageFlushService(this.logger, this.cacheDebugService, this.database);
    this.sortService = new StorageSortService(defaultSortModeName, persistSortMode);
  }

  public CompletableFuture<StorageCache> getOrLoad(String storageId) {
    StorageCache cache = caches.computeIfAbsent(storageId, this::createCache);
    if (cache.isLoaded()) {
      cache.touch();
      return CompletableFuture.completedFuture(cache);
    }
    CompletableFuture<StorageCache> loadFuture = new CompletableFuture<>();
    CompletableFuture<StorageCache> existingLoad = loading.putIfAbsent(storageId, loadFuture);
    if (existingLoad != null) {
      return existingLoad;
    }
    if (cache.isLoaded()) {
      cache.touch();
      loadFuture.complete(cache);
      loading.remove(storageId, loadFuture);
      return loadFuture;
    }
    database
        .ensureStorage(storageId)
        .thenCompose(
            v ->
                database
                    .loadStorage(storageId)
                    .thenCombine(database.getStorageSortMode(storageId), LoadedStorageData::new))
        .thenCompose(data -> finishLoadOnMainThread(storageId, cache, data))
        .whenComplete(
            (res, err) -> {
              try {
                if (err != null) {
                  loadFuture.completeExceptionally(err);
                } else {
                  loadFuture.complete(res);
                }
              } finally {
                loading.remove(storageId, loadFuture);
              }
            });
    return loadFuture;
  }

  private CompletableFuture<StorageCache> finishLoadOnMainThread(
      String storageId, StorageCache cache, LoadedStorageData data) {
    return supplyOnMainThread(
        "loading storage " + storageId,
        () -> {
          cache.loadFromDb(data.items());
          refreshCustomItems(cache);
          SortMode mode = sortService.resolveAndPersistDefault(storageId, data.sortMode());
          cache.setSortMode(mode);
          return cache;
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

  public void discardCacheForInternalCleanup(String storageId) {
    if (storageId == null) return;
    loading.remove(storageId);
    caches.remove(storageId);
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
                          toId, tierKey, tierMaxItems, snapshot.sortMode().name(), snapshot.items())
                      .thenCompose(ignored -> installClonedCache(toId, snapshot)));
    }
    return database.cloneStorage(fromId, toId, tierKey, tierMaxItems);
  }

  private CompletableFuture<CloneSnapshot> snapshotLoadedCacheForClone(
      StorageCache cache, String toId) {
    return supplyOnMainThread(
        "snapshotting loaded storage for clone " + toId,
        () -> {
          Collection<DbItem> items = cache.snapshotItems();
          Map<String, DbItem> byKey = new ConcurrentHashMap<>();
          for (DbItem item : items) {
            byKey.put(item.key(), item);
          }
          return new CloneSnapshot(items, byKey, cache.getSortMode());
        });
  }

  private CompletableFuture<Void> installClonedCache(String toId, CloneSnapshot snapshot) {
    return supplyOnMainThread(
        "installing cloned storage cache " + toId,
        () -> {
          StorageCache cloned = createCache(toId);
          cloned.loadFromDb(snapshot.byKey());
          cloned.setSortMode(snapshot.sortMode());
          caches.put(toId, cloned);
          return null;
        });
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

  private record LoadedStorageData(Map<String, DbItem> items, Optional<String> sortMode) {}

  private record CloneSnapshot(
      Collection<DbItem> items, Map<String, DbItem> byKey, SortMode sortMode) {}

  private StorageCache createCache(String storageId) {
    return new StorageCache(storageId, keys, logger, cacheDebugService);
  }

  private int refreshCustomItems(StorageCache cache) {
    return customItemRefresher.applyAsInt(cache);
  }

  private <T> CompletableFuture<T> supplyOnMainThread(String action, Supplier<T> supplier) {
    CompletableFuture<T> future = new CompletableFuture<>();
    if (!pluginEnabled.getAsBoolean()) {
      future.completeExceptionally(new IllegalStateException("Plugin disabled while " + action));
      return future;
    }
    Runnable task =
        () -> {
          if (!pluginEnabled.getAsBoolean()) {
            future.completeExceptionally(
                new IllegalStateException("Plugin disabled while " + action));
            return;
          }
          try {
            future.complete(supplier.get());
          } catch (RuntimeException e) {
            future.completeExceptionally(e);
          }
        };
    if (Bukkit.isPrimaryThread()) {
      task.run();
      return future;
    }
    try {
      Bukkit.getScheduler().runTask(schedulerPlugin, task);
    } catch (RuntimeException e) {
      future.completeExceptionally(e);
    }
    return future;
  }
}
