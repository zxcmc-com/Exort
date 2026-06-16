package com.zxcmc.exort.block.listener;

import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.MonitorDisplayManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

public record ItemPlaceBridgeDependencies(
    JavaPlugin plugin,
    StorageManager storageManager,
    CustomItems customItems,
    StorageKeys keys,
    Material wireMaterial,
    int wireHardCap,
    Material storageCarrier,
    Material terminalCarrier,
    Material monitorCarrier,
    Material busCarrier,
    Material relayCarrier,
    RegionProtection regionProtection,
    PlayerFeedback playerFeedback,
    Supplier<DisplayRefreshService> displayRefreshService,
    Supplier<ItemHologramManager> hologramManager,
    Supplier<MonitorDisplayManager> monitorDisplayManager,
    Supplier<BusService> busService,
    Supplier<NetworkGraphCache> networkGraphCache,
    Runnable revalidateSessions,
    Consumer<Block> monitorPlacedRecorder,
    BiFunction<String, String, CompletableFuture<Void>> storageTierSaver,
    Supplier<BreakSoundConfig> breakSoundConfig,
    Supplier<BusRuntimeConfig> busRuntimeConfig) {
  public ItemPlaceBridgeDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(customItems, "customItems");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(terminalCarrier, "terminalCarrier");
    Objects.requireNonNull(monitorCarrier, "monitorCarrier");
    Objects.requireNonNull(busCarrier, "busCarrier");
    Objects.requireNonNull(relayCarrier, "relayCarrier");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(hologramManager, "hologramManager");
    Objects.requireNonNull(monitorDisplayManager, "monitorDisplayManager");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(revalidateSessions, "revalidateSessions");
    Objects.requireNonNull(monitorPlacedRecorder, "monitorPlacedRecorder");
    Objects.requireNonNull(storageTierSaver, "storageTierSaver");
    Objects.requireNonNull(breakSoundConfig, "breakSoundConfig");
    Objects.requireNonNull(busRuntimeConfig, "busRuntimeConfig");
  }
}
