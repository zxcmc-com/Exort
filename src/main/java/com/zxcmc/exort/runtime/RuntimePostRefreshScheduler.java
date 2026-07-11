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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class RuntimePostRefreshScheduler {
  private static final long MODE_SWITCH_REFRESH_DELAY_TICKS = 5L;
  private static final int CHUNK_REFRESHES_PER_TICK = 8;
  private static final Map<Plugin, AtomicLong> GENERATIONS = new ConcurrentHashMap<>();

  private RuntimePostRefreshScheduler() {}

  public static Registration schedule(RuntimePostRefreshDependencies deps) {
    Objects.requireNonNull(deps, "deps");
    long generation =
        GENERATIONS.computeIfAbsent(deps.plugin(), ignored -> new AtomicLong()).incrementAndGet();
    Registration registration = new Registration(deps.plugin(), generation);
    registration.replaceTask(
        Bukkit.getScheduler()
            .runTaskLater(
                deps.plugin(),
                () -> refreshAfterRuntimeSwitch(deps, generation, registration),
                MODE_SWITCH_REFRESH_DELAY_TICKS));
    return registration;
  }

  private static void refreshAfterRuntimeSwitch(
      RuntimePostRefreshDependencies deps, long generation, Registration registration) {
    if (!isCurrent(deps.plugin(), generation)) {
      return;
    }
    NetworkGraphCache graphCache = deps.networkGraphCache().get();
    if (graphCache != null) {
      graphCache.invalidateAll();
    }
    List<Chunk> chunks = loadedChunksSnapshot();
    if (chunks.isEmpty()) {
      finishRefresh(deps, generation);
      return;
    }
    new ChunkRefreshRun(deps, chunks, generation, registration).run();
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

  private static void finishRefresh(RuntimePostRefreshDependencies deps, long generation) {
    if (!isCurrent(deps.plugin(), generation)) {
      return;
    }
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

  private static boolean isCurrent(Plugin plugin, long generation) {
    AtomicLong current = GENERATIONS.get(plugin);
    return current != null && current.get() == generation;
  }

  private static final class ChunkRefreshRun implements Runnable {
    private final RuntimePostRefreshDependencies deps;
    private final List<Chunk> chunks;
    private final long generation;
    private final Registration registration;
    private int cursor;

    ChunkRefreshRun(
        RuntimePostRefreshDependencies deps,
        List<Chunk> chunks,
        long generation,
        Registration registration) {
      this.deps = deps;
      this.chunks = chunks;
      this.generation = generation;
      this.registration = registration;
    }

    @Override
    public void run() {
      if (!isCurrent(deps.plugin(), generation)) {
        return;
      }
      int processed = 0;
      while (cursor < chunks.size() && processed < CHUNK_REFRESHES_PER_TICK) {
        refreshChunk(deps, chunks.get(cursor));
        cursor++;
        processed++;
      }
      if (cursor < chunks.size()) {
        registration.replaceTask(Bukkit.getScheduler().runTaskLater(deps.plugin(), this, 1L));
        return;
      }
      finishRefresh(deps, generation);
    }
  }

  /**
   * Owns the currently scheduled step so a failed runtime activation cannot refresh stale state.
   */
  public static final class Registration implements AutoCloseable {
    private final Plugin plugin;
    private final long generation;
    private final AtomicBoolean closed = new AtomicBoolean();
    private BukkitTask task;

    private Registration(Plugin plugin, long generation) {
      this.plugin = plugin;
      this.generation = generation;
    }

    private synchronized void replaceTask(BukkitTask replacement) {
      Objects.requireNonNull(replacement, "replacement");
      if (closed.get()) {
        replacement.cancel();
        return;
      }
      task = replacement;
    }

    @Override
    public synchronized void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      AtomicLong current = GENERATIONS.get(plugin);
      if (current != null) {
        current.compareAndSet(generation, generation + 1L);
      }
      if (task != null) {
        task.cancel();
        task = null;
      }
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
