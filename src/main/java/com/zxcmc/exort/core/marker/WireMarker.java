package com.zxcmc.exort.core.marker;

import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class WireMarker {
  private WireMarker() {}

  private static final String SECTION = "wire";
  private static final String FIELD_PRESENT = "present";

  public static void setWire(Plugin plugin, Block block) {
    ChunkMarkerStore.setByte(plugin, block, SECTION, FIELD_PRESENT, (byte) 1);
  }

  public static boolean isWire(Plugin plugin, Block block) {
    return ChunkMarkerStore.getByte(plugin, block, SECTION, FIELD_PRESENT)
        .map(val -> val == (byte) 1)
        .orElse(false);
  }

  public static void clearWire(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }
}
