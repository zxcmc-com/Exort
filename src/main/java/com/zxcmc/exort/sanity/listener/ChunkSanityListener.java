package com.zxcmc.exort.sanity.listener;

import com.zxcmc.exort.sanity.ChunkSanityService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

/**
 * Performs chunk sanity checks on load: - fixes carrier blocks for existing markers - removes stale
 * displays - clears orphaned marker data
 */
public final class ChunkSanityListener implements Listener {
  private final Plugin plugin;
  private final ChunkSanityService service;
  private final Runnable invalidateNetworkCache;

  public ChunkSanityListener(
      Plugin plugin, ChunkSanityService service, Runnable invalidateNetworkCache) {
    this.plugin = plugin;
    this.service = service;
    this.invalidateNetworkCache = invalidateNetworkCache;
  }

  @EventHandler
  public void onChunkLoad(ChunkLoadEvent event) {
    // Delay by 1 tick to ensure entities and PDC are available
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              service.sanitizeChunk(event.getChunk());
              invalidateNetworkCache.run();
            });
  }

  @EventHandler
  public void onChunkUnload(ChunkUnloadEvent event) {
    invalidateNetworkCache.run();
  }

  public void sanitizeChunk(Chunk chunk) {
    service.sanitizeChunk(chunk);
  }

  public void scanLoadedChunks() {
    service.scanLoadedChunks();
  }
}
