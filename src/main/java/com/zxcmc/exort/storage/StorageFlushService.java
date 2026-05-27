package com.zxcmc.exort.storage;

import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.infra.db.Database;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StorageFlushService {
  private final Logger logger;
  private final Supplier<CacheDebugService> cacheDebugService;
  private final Database database;

  public StorageFlushService(
      Logger logger, Supplier<CacheDebugService> cacheDebugService, Database database) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.cacheDebugService = Objects.requireNonNull(cacheDebugService, "cacheDebugService");
    this.database = Objects.requireNonNull(database, "database");
  }

  public CompletableFuture<Void> flushAsync(StorageCache cache) {
    StorageCache.DeltaSnapshot delta = cache.snapshotDeltaWithVersion();
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
    StorageCache.Snapshot snapshot = cache.snapshotWithVersion();
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
}
