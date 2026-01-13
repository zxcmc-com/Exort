package com.zxcmc.exort.storage;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.db.Database;
import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.gui.SortMode;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class StorageManager {
  private final ExortPlugin plugin;
  private final Database database;
  private final StorageFlushService flushService;
  private final StorageSortService sortService;
  private final Map<String, StorageCache> caches = new ConcurrentHashMap<>();
  // Tracks in-flight loads to prevent duplicate DB work for the same storage id.
  private final Map<String, CompletableFuture<StorageCache>> loading = new ConcurrentHashMap<>();

  public StorageManager(ExortPlugin plugin, Database database) {
    this.plugin = plugin;
    this.database = database;
    this.flushService = new StorageFlushService(plugin, database);
    this.sortService = new StorageSortService(plugin, database);
  }

  public CompletableFuture<StorageCache> getOrLoad(String storageId) {
    StorageCache cache =
        caches.computeIfAbsent(storageId, id -> new StorageCache(id, plugin.getKeys(), plugin));
    if (cache.isLoaded()) {
      cache.touch();
      return CompletableFuture.completedFuture(cache);
    }
    return loading.computeIfAbsent(
        storageId,
        id ->
            database
                .ensureStorage(storageId)
                .thenCompose(
                    v ->
                        database
                            .loadStorage(storageId)
                            .thenCombine(
                                database.getStorageSortMode(storageId),
                                (data, sort) -> {
                                  cache.loadFromDb(data);
                                  cache.refreshCustomItems(
                                      plugin.getCustomItems(), plugin.getWirelessService(), true);
                                  SortMode mode =
                                      sortService.resolveAndPersistDefault(storageId, sort);
                                  cache.setSortMode(mode);
                                  return cache;
                                }))
                .whenComplete((res, err) -> loading.remove(id)));
  }

  public void flushDirtyCaches() {
    for (StorageCache cache : caches.values()) {
      if (cache.isDirty()) {
        flush(cache);
      }
    }
  }

  public void flush(StorageCache cache) {
    flushService.flushAsync(cache);
  }

  public void flushAllAndWait() {
    CompletableFuture<?>[] futures =
        caches.values().stream()
            .filter(StorageCache::isDirty)
            .map(flushService::flushAsync)
            .toArray(CompletableFuture[]::new);
    if (futures.length == 0) return;
    try {
      CompletableFuture.allOf(futures).get();
    } catch (Exception e) {
      plugin.getLogger().log(Level.SEVERE, "Error waiting for storage flush", e);
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

  public int refreshLoadedCustomItems() {
    int refreshed = 0;
    for (StorageCache cache : caches.values()) {
      if (cache == null || !cache.isLoaded()) continue;
      refreshed +=
          cache.refreshCustomItems(plugin.getCustomItems(), plugin.getWirelessService(), true);
    }
    return refreshed;
  }

  public boolean isLoading(String storageId) {
    if (storageId == null) return false;
    return loading.containsKey(storageId);
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
    var debug = plugin.getCacheDebugService();
    if (debug != null && debug.isEnabled()) {
      debug.record(type, storageId, message, amount);
    }
  }
}
