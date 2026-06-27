package com.zxcmc.exort.sanity;

import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public final class ChunkSanityService {
  private final Plugin plugin;
  private final DisplayCleanupService displayCleanupService;
  private final MarkerSanityService markerSanityService;
  private final DisplayRefreshService displayRefreshService;
  private final Supplier<WorldEditDebugService> worldEditDebugService;
  private final Runnable invalidateNetwork;

  public ChunkSanityService(
      Plugin plugin,
      DisplayCleanupService displayCleanupService,
      MarkerSanityService markerSanityService,
      DisplayRefreshService displayRefreshService,
      Supplier<WorldEditDebugService> worldEditDebugService,
      Runnable invalidateNetwork) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.displayCleanupService =
        Objects.requireNonNull(displayCleanupService, "displayCleanupService");
    this.markerSanityService = Objects.requireNonNull(markerSanityService, "markerSanityService");
    this.displayRefreshService =
        Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    this.worldEditDebugService =
        Objects.requireNonNull(worldEditDebugService, "worldEditDebugService");
    this.invalidateNetwork = Objects.requireNonNull(invalidateNetwork, "invalidateNetwork");
  }

  public void sanitizeChunk(Chunk chunk) {
    int repairedWires = markerSanityService.repairFullChorusWires(chunk);
    displayCleanupService.cleanupDisplays(chunk);
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) {
      return;
    }
    displayCleanupService.cleanupDisplayMarkers(chunk);
    MarkerSanityService.CleanupResult cleanup = markerSanityService.cleanupStaleMarkers(chunk);
    recordSanityDebug(chunk, repairedWires, cleanup);
    displayRefreshService.refreshChunk(chunk);
    if (repairedWires > 0 || cleanup.changed()) {
      invalidateNetwork.run();
    }
  }

  public void scanLoadedChunks() {
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        sanitizeChunk(chunk);
      }
    }
  }

  private void recordSanityDebug(
      Chunk chunk, int repairedWires, MarkerSanityService.CleanupResult cleanup) {
    WorldEditDebugService debug = worldEditDebugService.get();
    if (debug == null || !debug.isFull()) {
      return;
    }
    if (repairedWires <= 0 && (cleanup == null || !cleanup.hasAnyRoot())) {
      return;
    }
    MarkerSanityService.CleanupResult result =
        cleanup == null ? MarkerSanityService.CleanupResult.empty() : cleanup;
    debug.recordEvent(
        "sanity chunk="
            + chunk.getX()
            + ","
            + chunk.getZ()
            + " roots accepted="
            + result.acceptedRoots()
            + " skipped="
            + result.skippedRoots()
            + " cleared="
            + result.clearedRoots()
            + " repaired="
            + repairedWires,
        NamedTextColor.BLUE);
  }
}
