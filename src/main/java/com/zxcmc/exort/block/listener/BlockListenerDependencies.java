package com.zxcmc.exort.block.listener;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.placement.storage.StorageTierSaver;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public record BlockListenerDependencies(
    JavaPlugin plugin,
    StorageManager storageManager,
    StorageKeys keys,
    CustomItems customItems,
    Material wireMaterial,
    int wireHardCap,
    ItemHologramManager hologramManager,
    Supplier<ItemHologramManager> hologramManagerSource,
    WireDisplayManager wireDisplayManager,
    Material storageCarrier,
    Material terminalCarrier,
    Material monitorCarrier,
    Material busCarrier,
    Material relayCarrier,
    BlockBreakHandler breakHandler,
    RegionProtection regionProtection,
    PlayerFeedback playerFeedback,
    Supplier<DisplayRefreshService> displayRefreshService,
    Supplier<MonitorDisplayManager> monitorDisplayManager,
    Supplier<BusService> busService,
    Supplier<NetworkGraphCache> networkGraphCache,
    Runnable revalidateSessions,
    StorageTierSaver storageTierSaver,
    Supplier<BreakSoundConfig> breakSoundConfig,
    Supplier<BusRuntimeConfig> busRuntimeConfig) {
  public BlockListenerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(customItems, "customItems");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(hologramManagerSource, "hologramManagerSource");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(terminalCarrier, "terminalCarrier");
    Objects.requireNonNull(monitorCarrier, "monitorCarrier");
    Objects.requireNonNull(busCarrier, "busCarrier");
    Objects.requireNonNull(relayCarrier, "relayCarrier");
    Objects.requireNonNull(breakHandler, "breakHandler");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(monitorDisplayManager, "monitorDisplayManager");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(revalidateSessions, "revalidateSessions");
    Objects.requireNonNull(storageTierSaver, "storageTierSaver");
    Objects.requireNonNull(breakSoundConfig, "breakSoundConfig");
    Objects.requireNonNull(busRuntimeConfig, "busRuntimeConfig");
  }
}
