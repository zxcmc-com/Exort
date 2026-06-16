package com.zxcmc.exort.command;

import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

record DebugCacheStatusRendererDependencies(
    JavaPlugin plugin,
    Lang lang,
    StorageManager storageManager,
    StorageKeys keys,
    LongSupplier cacheIdleUnloadSeconds,
    IntSupplier wireLimit,
    IntSupplier wireHardCap,
    IntSupplier relayRangeChunks,
    Supplier<Material> wireMaterial,
    Supplier<Material> storageCarrier,
    Supplier<Material> relayCarrier) {
  DebugCacheStatusRendererDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(lang, "lang");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(cacheIdleUnloadSeconds, "cacheIdleUnloadSeconds");
    Objects.requireNonNull(wireLimit, "wireLimit");
    Objects.requireNonNull(wireHardCap, "wireHardCap");
    Objects.requireNonNull(relayRangeChunks, "relayRangeChunks");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(relayCarrier, "relayCarrier");
  }
}
