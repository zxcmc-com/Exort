package com.zxcmc.exort.core.marker;

import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/** Chunk-level marker for storage core blocks (dummy storage base). */
public final class StorageCoreMarker {
  private StorageCoreMarker() {}

  private static final String SECTION = "storage_core";
  private static final String FIELD_PRESENT = "present";

  public static void set(Plugin plugin, Block block) {
    ChunkMarkerStore.setByte(plugin, block, SECTION, FIELD_PRESENT, (byte) 1);
  }

  public static boolean isCore(Plugin plugin, Block block) {
    return ChunkMarkerStore.getByte(plugin, block, SECTION, FIELD_PRESENT)
        .map(val -> val == (byte) 1)
        .orElse(false);
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }
}
