package com.zxcmc.exort.core.sanity;

import com.zxcmc.exort.display.DisplayRefreshService;
import org.bukkit.Bukkit;

public final class ChunkSanityService {
    private final DisplayCleanupService displayCleanupService;
    private final MarkerSanityService markerSanityService;
    private final DisplayRefreshService displayRefreshService;

    public ChunkSanityService(DisplayCleanupService displayCleanupService,
                              MarkerSanityService markerSanityService,
                              DisplayRefreshService displayRefreshService) {
        this.displayCleanupService = displayCleanupService;
        this.markerSanityService = markerSanityService;
        this.displayRefreshService = displayRefreshService;
    }

    public void sanitizeChunk(org.bukkit.Chunk chunk) {
        displayCleanupService.cleanupDisplays(chunk);
        if (chunk.getPersistentDataContainer().getKeys().isEmpty()) {
            return;
        }
        displayCleanupService.cleanupDisplayMarkers(chunk);
        markerSanityService.cleanupStaleMarkers(chunk);
        displayRefreshService.refreshChunk(chunk);
    }

    public void scanLoadedChunks() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                sanitizeChunk(chunk);
            }
        }
    }

}
