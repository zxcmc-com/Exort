package com.zxcmc.exort.marker;

import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/** Chunk-level marker for monitor blocks (all modes). */
public final class MonitorMarker {
  private MonitorMarker() {}

  private static final String SECTION = "monitor";
  private static final String FIELD_FACING = "facing";
  private static final String FIELD_ITEM_KEY = "item_key";
  private static final String FIELD_ITEM_BLOB = "item_blob";

  public static void set(Plugin plugin, Block block, BlockFace facing) {
    BlockFace safeFacing = facing == null ? BlockFace.SOUTH : facing;
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_FACING, safeFacing.name());
  }

  public static boolean isMonitor(Plugin plugin, Block block) {
    return ChunkMarkerStore.hasSection(plugin, block, SECTION);
  }

  public static Optional<BlockFace> facing(Plugin plugin, Block block) {
    String raw = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_FACING).orElse(null);
    if (raw == null) return Optional.empty();
    try {
      return Optional.of(BlockFace.valueOf(raw));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  public static void setItem(Plugin plugin, Block block, String itemKey, byte[] itemBlob) {
    if (itemKey != null) {
      ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_ITEM_KEY, itemKey);
    }
    if (itemBlob != null) {
      ChunkMarkerStore.setBytes(plugin, block, SECTION, FIELD_ITEM_BLOB, itemBlob);
    }
  }

  public static Optional<String> itemKey(Plugin plugin, Block block) {
    return ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_ITEM_KEY);
  }

  public static Optional<byte[]> itemBlob(Plugin plugin, Block block) {
    return ChunkMarkerStore.getBytes(plugin, block, SECTION, FIELD_ITEM_BLOB);
  }

  public static void clearItem(Plugin plugin, Block block) {
    ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_ITEM_KEY);
    ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_ITEM_BLOB);
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }
}
