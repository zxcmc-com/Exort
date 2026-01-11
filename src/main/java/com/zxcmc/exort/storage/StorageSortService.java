package com.zxcmc.exort.storage;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.db.Database;
import com.zxcmc.exort.gui.SortMode;

import java.util.Optional;

public final class StorageSortService {
    private final ExortPlugin plugin;
    private final Database database;

    public StorageSortService(ExortPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public SortMode resolveAndPersistDefault(String storageId, Optional<String> stored) {
        SortMode defaultMode = SortMode.fromString(plugin.getConfig().getString("defaultSortMode", "AMOUNT"));
        SortMode mode = stored.isEmpty() ? defaultMode : SortMode.fromString(stored.orElse(null));
        if (stored.isEmpty()) {
            database.setStorageSortMode(storageId, mode.name());
        }
        return mode;
    }
}
