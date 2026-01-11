package com.zxcmc.exort.storage;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.db.Database;
import com.zxcmc.exort.debug.CacheDebugService;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class StorageFlushService {
    private final ExortPlugin plugin;
    private final Database database;

    public StorageFlushService(ExortPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public CompletableFuture<Void> flushAsync(StorageCache cache) {
        StorageCache.DeltaSnapshot delta = cache.snapshotDeltaWithVersion();
        if (!delta.upserts().isEmpty() || !delta.removals().isEmpty()) {
            int count = delta.upserts().size() + delta.removals().size();
            log(CacheDebugService.EventType.FLUSH, cache.getStorageId(), "cache flush: " + cache.getStorageId() + " items=" + count);
            return database.writeDelta(cache.getStorageId(), delta.upserts(), delta.removals())
                    .whenComplete((res, err) -> {
                        if (err != null) {
                            plugin.getLogger().log(Level.SEVERE, "Failed to flush storage " + cache.getStorageId(), err);
                        } else {
                            cache.markCleanIfVersion(delta.version());
                        }
                    }).thenAccept(ignored -> {
                    });
        }
        StorageCache.Snapshot snapshot = cache.snapshotWithVersion();
        log(CacheDebugService.EventType.FLUSH, cache.getStorageId(), "cache flush: " + cache.getStorageId() + " items=" + snapshot.items().size());
        return database.writeSnapshot(cache.getStorageId(), snapshot.items())
                .whenComplete((res, err) -> {
                    if (err != null) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to flush storage " + cache.getStorageId(), err);
                    } else {
                        cache.markCleanIfVersion(snapshot.version());
                    }
                }).thenAccept(ignored -> {
                });
    }

    private void log(CacheDebugService.EventType type, String storageId, String message) {
        var debug = plugin.getCacheDebugService();
        if (debug != null && debug.isEnabled()) {
            debug.record(type, storageId, message);
        }
    }
}
