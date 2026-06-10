package com.zxcmc.exort.storage;

import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.infra.db.Database;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StorageFlushService {
  private static final int MAX_BATCH_STORAGES = 32;
  private static final int MAX_BATCH_ROWS = 2000;

  private final Logger logger;
  private final Supplier<CacheDebugService> cacheDebugService;
  private final Database database;
  private final Map<String, CompletableFuture<Void>> inFlightFlushes = new ConcurrentHashMap<>();

  public StorageFlushService(
      Logger logger, Supplier<CacheDebugService> cacheDebugService, Database database) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.cacheDebugService = Objects.requireNonNull(cacheDebugService, "cacheDebugService");
    this.database = Objects.requireNonNull(database, "database");
  }

  public CompletableFuture<Void> flushAsync(StorageCache cache) {
    String storageId = cache.getStorageId();
    CompletableFuture<Void> placeholder = new CompletableFuture<>();
    CompletableFuture<Void> existing = inFlightFlushes.putIfAbsent(storageId, placeholder);
    if (existing != null) {
      return existing;
    }
    flushUntilCleanAsync(cache)
        .whenComplete(
            (ignored, err) -> {
              inFlightFlushes.remove(storageId, placeholder);
              if (err != null) {
                placeholder.completeExceptionally(err);
              } else {
                placeholder.complete(null);
              }
            });
    return placeholder;
  }

  public CompletableFuture<Void> flushBatchAsync(Collection<StorageCache> caches) {
    if (caches == null || caches.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    List<PendingDeltaFlush> pendingDeltas = new ArrayList<>();
    int rows = 0;
    for (StorageCache cache : caches) {
      if (cache == null || !cache.isDirty()) {
        continue;
      }
      String storageId = cache.getStorageId();
      CompletableFuture<Void> placeholder = new CompletableFuture<>();
      CompletableFuture<Void> existing = inFlightFlushes.putIfAbsent(storageId, placeholder);
      if (existing != null) {
        futures.add(existing);
        continue;
      }
      StorageCache.DeltaSnapshot delta;
      try {
        delta = cache.snapshotDeltaWithVersion();
      } catch (RuntimeException e) {
        inFlightFlushes.remove(storageId, placeholder);
        logger.log(Level.SEVERE, "Failed to prepare storage flush " + storageId, e);
        placeholder.completeExceptionally(e);
        futures.add(placeholder);
        continue;
      }
      int deltaRows = delta.upserts().size() + delta.removals().size();
      if (deltaRows <= 0
          || pendingDeltas.size() >= MAX_BATCH_STORAGES
          || rows + deltaRows > MAX_BATCH_ROWS) {
        inFlightFlushes.remove(storageId, placeholder);
        futures.add(flushAsync(cache));
        continue;
      }
      rows += deltaRows;
      pendingDeltas.add(new PendingDeltaFlush(cache, delta, placeholder));
      futures.add(placeholder);
    }
    if (!pendingDeltas.isEmpty()) {
      flushPendingDeltas(pendingDeltas);
    }
    if (futures.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
  }

  private CompletableFuture<Void> flushUntilCleanAsync(StorageCache cache) {
    return flushOnceAsync(cache)
        .thenCompose(
            ignored ->
                cache.isDirty()
                    ? flushUntilCleanAsync(cache)
                    : CompletableFuture.completedFuture(null));
  }

  private void flushPendingDeltas(List<PendingDeltaFlush> pendingDeltas) {
    List<Database.DeltaWrite> writes =
        pendingDeltas.stream()
            .map(
                pending ->
                    new Database.DeltaWrite(
                        pending.cache().getStorageId(),
                        pending.delta().upserts(),
                        pending.delta().removals()))
            .toList();
    database
        .writeDeltaBatch(writes)
        .whenComplete(
            (ignored, err) -> {
              for (PendingDeltaFlush pending : pendingDeltas) {
                completePendingDelta(pending, err);
              }
            });
  }

  private void completePendingDelta(PendingDeltaFlush pending, Throwable err) {
    StorageCache cache = pending.cache();
    String storageId = cache.getStorageId();
    inFlightFlushes.remove(storageId, pending.future());
    if (err != null) {
      logger.log(Level.SEVERE, "Failed to flush storage " + storageId, err);
      pending.future().completeExceptionally(err);
      return;
    }
    cache.markCleanIfVersion(pending.delta().version());
    if (!cache.isDirty()) {
      pending.future().complete(null);
      return;
    }
    flushAsync(cache)
        .whenComplete(
            (ignored, nextErr) -> {
              if (nextErr != null) {
                pending.future().completeExceptionally(nextErr);
              } else {
                pending.future().complete(null);
              }
            });
  }

  private CompletableFuture<Void> flushOnceAsync(StorageCache cache) {
    StorageCache.DeltaSnapshot delta;
    try {
      delta = cache.snapshotDeltaWithVersion();
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "Failed to prepare storage flush " + cache.getStorageId(), e);
      return CompletableFuture.failedFuture(e);
    }
    if (!delta.upserts().isEmpty() || !delta.removals().isEmpty()) {
      int count = delta.upserts().size() + delta.removals().size();
      log(
          CacheDebugService.EventType.FLUSH,
          cache.getStorageId(),
          "cache flush: " + cache.getStorageId() + " items=" + count);
      return database
          .writeDelta(cache.getStorageId(), delta.upserts(), delta.removals())
          .whenComplete(
              (res, err) -> {
                if (err != null) {
                  logger.log(Level.SEVERE, "Failed to flush storage " + cache.getStorageId(), err);
                } else {
                  cache.markCleanIfVersion(delta.version());
                }
              })
          .thenAccept(ignored -> {});
    }
    StorageCache.Snapshot snapshot;
    try {
      snapshot = cache.snapshotWithVersion();
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "Failed to prepare storage snapshot " + cache.getStorageId(), e);
      return CompletableFuture.failedFuture(e);
    }
    log(
        CacheDebugService.EventType.FLUSH,
        cache.getStorageId(),
        "cache flush: " + cache.getStorageId() + " items=" + snapshot.items().size());
    return database
        .writeSnapshot(cache.getStorageId(), snapshot.items())
        .whenComplete(
            (res, err) -> {
              if (err != null) {
                logger.log(Level.SEVERE, "Failed to flush storage " + cache.getStorageId(), err);
              } else {
                cache.markCleanIfVersion(snapshot.version());
              }
            })
        .thenAccept(ignored -> {});
  }

  private void log(CacheDebugService.EventType type, String storageId, String message) {
    var debug = cacheDebugService.get();
    if (debug != null && debug.isEnabled()) {
      debug.record(type, storageId, message);
    }
  }

  private record PendingDeltaFlush(
      StorageCache cache, StorageCache.DeltaSnapshot delta, CompletableFuture<Void> future) {}
}
