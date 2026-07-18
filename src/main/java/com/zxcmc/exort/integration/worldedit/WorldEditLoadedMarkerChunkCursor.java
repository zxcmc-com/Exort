package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.marker.ChunkMarkerStore;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/** Bounded main-thread cursor for legitimate world-wide entity refresh commands. */
final class WorldEditLoadedMarkerChunkCursor {
  static final int CHUNKS_PER_TICK = 64;

  private final Plugin plugin;
  private final Chunk[] loadedChunks;
  private final Consumer<Set<ChunkKey>> completion;
  private final Set<ChunkKey> captured = new HashSet<>();
  private BukkitTask task;
  private int index;

  WorldEditLoadedMarkerChunkCursor(Plugin plugin, World world, Consumer<Set<ChunkKey>> completion) {
    this.plugin = plugin;
    this.loadedChunks = world == null ? new Chunk[0] : world.getLoadedChunks();
    this.completion = completion;
  }

  void start() {
    if (task != null) return;
    task =
        new BukkitRunnable() {
          @Override
          public void run() {
            int examined = 0;
            while (examined < CHUNKS_PER_TICK && index < loadedChunks.length) {
              Chunk chunk = loadedChunks[index++];
              examined++;
              if (chunk != null && ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) {
                captured.add(new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
              }
            }
            if (index >= loadedChunks.length) {
              finish();
            }
          }
        }.runTaskTimer(plugin, 1L, 1L);
  }

  void cancel() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }

  int examined() {
    return index;
  }

  private void finish() {
    cancel();
    if (completion != null) completion.accept(Set.copyOf(captured));
  }
}
