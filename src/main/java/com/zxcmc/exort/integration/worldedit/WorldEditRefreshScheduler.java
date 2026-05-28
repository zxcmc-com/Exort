package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.sanity.DisplayCleanupService;
import java.util.Set;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

final class WorldEditRefreshScheduler {
  private static final long[] DEFERRED_REFRESH_DELAYS_TICKS = {2L, 10L};

  private final WorldEditBridgeDependencies deps;

  WorldEditRefreshScheduler(WorldEditBridgeDependencies deps) {
    this.deps = deps;
  }

  void refreshAffectedChunks(
      Set<ChunkKey> chunkKeys, Set<BlockRef> networkRefreshStarts, String reason) {
    if (chunkKeys == null || chunkKeys.isEmpty()) {
      return;
    }
    DisplayRefreshService refreshService = deps.displayRefreshService();
    BusService busService = deps.busService();
    DisplayCleanupService cleanupService = newDisplayCleanupService();
    int refreshedChunks = 0;
    for (ChunkKey key : chunkKeys) {
      World world = Bukkit.getWorld(key.worldId());
      if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
        continue;
      }
      Chunk chunk = world.getChunkAt(key.chunkX(), key.chunkZ());
      cleanupService.cleanupDisplays(chunk);
      if (refreshService != null) {
        refreshService.refreshChunk(chunk);
      }
      if (busService != null) {
        busService.scanChunk(chunk);
      }
      refreshedChunks++;
    }
    if (refreshService != null && networkRefreshStarts != null) {
      for (BlockRef ref : networkRefreshStarts) {
        Block block = ref.block();
        if (block == null) {
          continue;
        }
        refreshService.refreshNetworkFrom(block);
      }
    }
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isFull()) {
      debug.recordEvent(
          "we refresh pass reason="
              + reason
              + " chunks="
              + refreshedChunks
              + " starts="
              + (networkRefreshStarts == null ? 0 : networkRefreshStarts.size()),
          NamedTextColor.BLUE);
    }
  }

  void scheduleDeferredRefresh(Set<ChunkKey> chunkKeys, Set<BlockRef> networkRefreshStarts) {
    Set<ChunkKey> chunks = Set.copyOf(chunkKeys);
    Set<BlockRef> starts =
        networkRefreshStarts == null || networkRefreshStarts.isEmpty()
            ? Set.of()
            : Set.copyOf(networkRefreshStarts);
    for (long delay : DEFERRED_REFRESH_DELAYS_TICKS) {
      try {
        Bukkit.getScheduler()
            .runTaskLater(
                deps.plugin(),
                () -> {
                  if (!deps.plugin().isEnabled()) {
                    return;
                  }
                  refreshAffectedChunks(chunks, starts, "deferred_" + delay + "t");
                },
                delay);
      } catch (RuntimeException ignored) {
        // The plugin may be disabling while a WorldEdit flush finishes.
      }
    }
  }

  private DisplayCleanupService newDisplayCleanupService() {
    return new DisplayCleanupService(
        deps.plugin(),
        deps.wireMaterial(),
        deps.storageCarrier(),
        deps.terminalCarrier(),
        deps.monitorCarrier(),
        deps.busCarrier());
  }
}
