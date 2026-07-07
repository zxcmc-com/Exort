package com.zxcmc.exort.marker;

import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class TransmitterMarker {
  private TransmitterMarker() {}

  public static final String SECTION = "transmitter";
  private static final String FIELD_PRESENT = "present";

  public static void set(Plugin plugin, Block block) {
    ChunkMarkerStore.setByte(plugin, block, SECTION, FIELD_PRESENT, (byte) 1);
  }

  public static boolean isTransmitter(Plugin plugin, Block block) {
    return ChunkMarkerStore.getByte(plugin, block, SECTION, FIELD_PRESENT)
        .map(val -> val == (byte) 1)
        .orElse(false);
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }
}
