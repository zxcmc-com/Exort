package com.zxcmc.exort.core.marker;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Chunk-level marker for import/export buses.
 * Format: type:IMPORT;facing:NORTH;mode:DISABLED
 */
public final class BusMarker {
    private BusMarker() {
    }

    private static final String PREFIX = "bus";
    private static final String KEY_TYPE = "type";
    private static final String KEY_FACING = "facing";
    private static final String KEY_MODE = "mode";

    public record Data(BusType type, BlockFace facing, BusMode mode) {
    }

    public static void set(Plugin plugin, Block block, BusType type, BlockFace facing, BusMode mode) {
        BusType safeType = type == null ? BusType.IMPORT : type;
        BusMode safeMode = mode == null ? BusMode.DISABLED : mode;
        BlockFace safeFacing = facing == null ? BlockFace.NORTH : facing;
        String value = KEY_TYPE + ":" + safeType.name()
                + ";" + KEY_FACING + ":" + safeFacing.name()
                + ";" + KEY_MODE + ":" + safeMode.name();
        ChunkMarkerStore.setMarker(plugin, PREFIX, block, value);
    }

    public static Optional<Data> get(Plugin plugin, Block block) {
        return ChunkMarkerStore.getMarker(plugin, PREFIX, block)
                .flatMap(raw -> {
                    if (raw == null) return Optional.empty();
                    BusType type = null;
                    BusMode mode = null;
                    BlockFace facing = null;
                    for (String part : raw.split(";")) {
                        if (part.startsWith(KEY_TYPE + ":")) {
                            type = BusType.fromString(part.substring((KEY_TYPE + ":").length()));
                        } else if (part.startsWith(KEY_FACING + ":")) {
                            String dir = part.substring((KEY_FACING + ":").length());
                            try {
                                facing = BlockFace.valueOf(dir);
                            } catch (IllegalArgumentException ignored) {
                                facing = null;
                            }
                        } else if (part.startsWith(KEY_MODE + ":")) {
                            mode = BusMode.fromString(part.substring((KEY_MODE + ":").length()));
                        }
                    }
                    if (type == null) type = BusType.IMPORT;
                    if (mode == null) mode = BusMode.DISABLED;
                    if (facing == null) facing = BlockFace.NORTH;
                    return Optional.of(new Data(type, facing, mode));
                });
    }

    public static boolean isBus(Plugin plugin, Block block) {
        return ChunkMarkerStore.getMarker(plugin, PREFIX, block).isPresent();
    }

    public static void clear(Plugin plugin, Block block) {
        ChunkMarkerStore.clearMarker(plugin, PREFIX, block);
    }
}
