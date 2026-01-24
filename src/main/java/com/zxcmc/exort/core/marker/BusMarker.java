package com.zxcmc.exort.core.marker;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusType;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/** Chunk-level marker for import/export buses. Format: type:IMPORT;facing:NORTH;mode:DISABLED */
public final class BusMarker {
  private BusMarker() {}

  private static final String SECTION = "bus";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_FACING = "facing";
  private static final String FIELD_MODE = "mode";
  private static final String FIELD_FILTERS = "filters";

  public record Data(BusType type, BlockFace facing, BusMode mode) {}

  public static void set(Plugin plugin, Block block, BusType type, BlockFace facing, BusMode mode) {
    BusType safeType = type == null ? BusType.IMPORT : type;
    BusMode safeMode = mode == null ? BusMode.DISABLED : mode;
    BlockFace safeFacing = facing == null ? BlockFace.NORTH : facing;
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_TYPE, safeType.name());
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_FACING, safeFacing.name());
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_MODE, safeMode.name());
  }

  public static void setFilters(Plugin plugin, Block block, byte[] filters) {
    if (filters == null) return;
    ChunkMarkerStore.setBytes(plugin, block, SECTION, FIELD_FILTERS, filters);
  }

  public static Optional<byte[]> getFilters(Plugin plugin, Block block) {
    return ChunkMarkerStore.getBytes(plugin, block, SECTION, FIELD_FILTERS);
  }

  public static Optional<Data> get(Plugin plugin, Block block) {
    if (!ChunkMarkerStore.hasSection(plugin, block, SECTION)) return Optional.empty();
    BusType type =
        BusType.fromString(
            ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_TYPE)
                .orElse(BusType.IMPORT.name()));
    BusMode mode =
        BusMode.fromString(
            ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_MODE)
                .orElse(BusMode.DISABLED.name()));
    BlockFace facing;
    String facingRaw =
        ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_FACING)
            .orElse(BlockFace.NORTH.name());
    try {
      facing = BlockFace.valueOf(facingRaw);
    } catch (IllegalArgumentException ignored) {
      facing = BlockFace.NORTH;
    }
    return Optional.of(new Data(type, facing, mode));
  }

  public static boolean isBus(Plugin plugin, Block block) {
    return ChunkMarkerStore.hasSection(plugin, block, SECTION);
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }
}
