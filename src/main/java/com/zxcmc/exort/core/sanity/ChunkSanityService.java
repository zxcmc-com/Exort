package com.zxcmc.exort.core.sanity;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.marker.ChunkMarkerStore;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.DisplayRefreshService;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

public final class ChunkSanityService {
  private final ExortPlugin plugin;
  private final DisplayCleanupService displayCleanupService;
  private final MarkerSanityService markerSanityService;
  private final DisplayRefreshService displayRefreshService;

  public ChunkSanityService(
      ExortPlugin plugin,
      DisplayCleanupService displayCleanupService,
      MarkerSanityService markerSanityService,
      DisplayRefreshService displayRefreshService) {
    this.plugin = plugin;
    this.displayCleanupService = displayCleanupService;
    this.markerSanityService = markerSanityService;
    this.displayRefreshService = displayRefreshService;
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
    if ((repairedWires > 0 || cleanup.changed()) && plugin.getNetworkGraphCache() != null) {
      plugin.getNetworkGraphCache().invalidateAll();
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
    WorldEditDebugService debug = plugin.getWorldEditDebugService();
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
