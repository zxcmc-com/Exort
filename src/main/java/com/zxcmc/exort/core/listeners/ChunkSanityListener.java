package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.sanity.ChunkSanityService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Performs chunk sanity checks on load:
 * - fixes carrier blocks for existing markers
 * - removes stale displays
 * - clears orphaned marker data
 */
public class ChunkSanityListener implements Listener {
    private final ExortPlugin plugin;
    private final ChunkSanityService service;

    public ChunkSanityListener(ExortPlugin plugin, ChunkSanityService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Delay by 1 tick to ensure entities and PDC are available
        Bukkit.getScheduler().runTask(plugin, () -> {
            service.sanitizeChunk(event.getChunk());
            if (plugin.getNetworkGraphCache() != null) {
                plugin.getNetworkGraphCache().invalidateAll();
            }
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (plugin.getNetworkGraphCache() != null) {
            plugin.getNetworkGraphCache().invalidateAll();
        }
    }

    public void sanitizeChunk(org.bukkit.Chunk chunk) {
        service.sanitizeChunk(chunk);
    }

    public void scanLoadedChunks() {
        service.scanLoadedChunks();
    }
}
