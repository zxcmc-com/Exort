package com.zxcmc.exort.runtime;

import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.items.InventoryRefreshService;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class RuntimePostRefreshScheduler {
  private static final long MODE_SWITCH_REFRESH_DELAY_TICKS = 5L;

  private RuntimePostRefreshScheduler() {}

  public static void schedule(RuntimePostRefreshDependencies deps) {
    Objects.requireNonNull(deps, "deps");
    Bukkit.getScheduler()
        .runTaskLater(
            deps.plugin(), () -> refreshAfterRuntimeSwitch(deps), MODE_SWITCH_REFRESH_DELAY_TICKS);
  }

  private static void refreshAfterRuntimeSwitch(RuntimePostRefreshDependencies deps) {
    NetworkGraphCache graphCache = deps.networkGraphCache().get();
    if (graphCache != null) {
      graphCache.invalidateAll();
    }
    DisplayRefreshService displayRefreshService = deps.displayRefreshService();
    for (var world : Bukkit.getWorlds()) {
      for (var chunk : world.getLoadedChunks()) {
        displayRefreshService.refreshChunk(chunk);
        if (!ChunkMarkerStore.hasAnyBlockData(deps.plugin(), chunk)) continue;
        ChunkMarkerStore.forEachBlock(
            deps.plugin(),
            chunk,
            (block, root) -> {
              if (StorageMarker.get(deps.plugin(), block).isPresent()) {
                displayRefreshService.refreshNetworkFrom(block);
              }
            });
      }
    }
    StorageManager storageManager = deps.storageManager();
    if (storageManager != null) {
      storageManager.refreshLoadedCustomItems();
    }
    InventoryRefreshService inventoryRefreshService = deps.inventoryRefreshService();
    for (var player : Bukkit.getOnlinePlayers()) {
      inventoryRefreshService.refreshPlayerInventory(player);
    }
    inventoryRefreshService.bumpEpoch();
  }

  public record RuntimePostRefreshDependencies(
      Plugin plugin,
      Supplier<NetworkGraphCache> networkGraphCache,
      DisplayRefreshService displayRefreshService,
      StorageManager storageManager,
      InventoryRefreshService inventoryRefreshService) {
    public RuntimePostRefreshDependencies {
      Objects.requireNonNull(plugin, "plugin");
      Objects.requireNonNull(networkGraphCache, "networkGraphCache");
      Objects.requireNonNull(displayRefreshService, "displayRefreshService");
      Objects.requireNonNull(inventoryRefreshService, "inventoryRefreshService");
    }
  }
}
