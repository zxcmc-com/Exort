package com.zxcmc.exort.core.marker;

import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/** Chunk-level marker for storage core blocks (dummy storage base). */
public final class StorageCoreMarker {
  private StorageCoreMarker() {}

  private static final String PREFIX = "storage_core";

  public static void set(Plugin plugin, Block block) {
    ChunkMarkerStore.setMarker(plugin, PREFIX, block, "core");
  }

  public static boolean isCore(Plugin plugin, Block block) {
    return ChunkMarkerStore.getMarker(plugin, PREFIX, block).isPresent();
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearMarker(plugin, PREFIX, block);
  }
}
