package com.zxcmc.exort.core.sanity;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.marker.ChunkMarkerStore;
import com.zxcmc.exort.display.DisplayRefreshService;
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
    displayCleanupService.cleanupDisplays(chunk);
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) {
      return;
    }
    displayCleanupService.cleanupDisplayMarkers(chunk);
    markerSanityService.cleanupStaleMarkers(chunk);
    displayRefreshService.refreshChunk(chunk);
  }

  public void scanLoadedChunks() {
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        sanitizeChunk(chunk);
      }
    }
  }
}
