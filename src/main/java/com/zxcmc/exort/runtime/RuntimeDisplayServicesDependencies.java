package com.zxcmc.exort.runtime;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public record RuntimeDisplayServicesDependencies(
    JavaPlugin plugin,
    ConfigurationSection config,
    Lang lang,
    StorageKeys keys,
    StorageManager storageManager,
    Database database,
    RuntimeMaterials materials,
    RuntimeItemModelConfig itemModels,
    RuntimeHologramConfig hologramConfig,
    boolean resourceMode,
    int wireLimit,
    int wireHardCap,
    RuntimeDisplayConfig.MaterialResolver materialResolver,
    Supplier<WorldEditDebugService> worldEditDebugService,
    Supplier<BusService> busService,
    Runnable invalidateNetwork) {
  public RuntimeDisplayServicesDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(lang, "lang");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(materials, "materials");
    Objects.requireNonNull(itemModels, "itemModels");
    Objects.requireNonNull(hologramConfig, "hologramConfig");
    Objects.requireNonNull(materialResolver, "materialResolver");
    Objects.requireNonNull(worldEditDebugService, "worldEditDebugService");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(invalidateNetwork, "invalidateNetwork");
  }
}
