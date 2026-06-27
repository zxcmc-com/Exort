package com.zxcmc.exort.runtime;

import com.zxcmc.exort.command.ExortGiveMenu;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.items.InventoryRefreshService;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.storage.StorageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.plugin.Plugin;

public final class RuntimePostRefreshScheduler {
  private static final long MODE_SWITCH_REFRESH_DELAY_TICKS = 5L;
  private static final int CHUNK_REFRESHES_PER_TICK = 8;

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
    List<Chunk> chunks = loadedChunksSnapshot();
    if (chunks.isEmpty()) {
      finishRefresh(deps);
      return;
    }
    new ChunkRefreshRun(deps, chunks).run();
  }

  private static List<Chunk> loadedChunksSnapshot() {
    List<Chunk> chunks = new ArrayList<>();
    for (var world : Bukkit.getWorlds()) {
      for (var chunk : world.getLoadedChunks()) {
        chunks.add(chunk);
      }
    }
    return chunks;
  }

  private static void refreshChunk(RuntimePostRefreshDependencies deps, Chunk chunk) {
    if (chunk == null || !chunk.isLoaded()) {
      return;
    }
    DisplayRefreshService displayRefreshService = deps.displayRefreshService();
    displayRefreshService.refreshChunk(chunk);
    if (!ChunkMarkerStore.hasAnyBlockData(deps.plugin(), chunk)) return;
    ChunkMarkerStore.forEachBlock(
        deps.plugin(),
        chunk,
        (block, root) -> {
          if (StorageMarker.get(deps.plugin(), block).isPresent()) {
            displayRefreshService.refreshNetworkFrom(block);
          }
        });
  }

  private static void finishRefresh(RuntimePostRefreshDependencies deps) {
    StorageManager storageManager = deps.storageManager();
    if (storageManager != null) {
      storageManager.refreshLoadedCustomItems();
    }
    InventoryRefreshService inventoryRefreshService = deps.inventoryRefreshService();
    for (var player : Bukkit.getOnlinePlayers()) {
      inventoryRefreshService.refreshPlayerInventory(player);
      if (player.getOpenInventory().getTopInventory().getHolder() instanceof ExortGiveMenu menu) {
        menu.refreshCatalog();
      }
    }
    inventoryRefreshService.bumpEpoch();
  }

  private static final class ChunkRefreshRun implements Runnable {
    private final RuntimePostRefreshDependencies deps;
    private final List<Chunk> chunks;
    private int cursor;

    ChunkRefreshRun(RuntimePostRefreshDependencies deps, List<Chunk> chunks) {
      this.deps = deps;
      this.chunks = chunks;
    }

    @Override
    public void run() {
      int processed = 0;
      while (cursor < chunks.size() && processed < CHUNK_REFRESHES_PER_TICK) {
        refreshChunk(deps, chunks.get(cursor));
        cursor++;
        processed++;
      }
      if (cursor < chunks.size()) {
        Bukkit.getScheduler().runTaskLater(deps.plugin(), this, 1L);
        return;
      }
      finishRefresh(deps);
    }
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
