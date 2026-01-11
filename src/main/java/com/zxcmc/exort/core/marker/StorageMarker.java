package com.zxcmc.exort.core.marker;

import com.zxcmc.exort.storage.StorageTier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Chunk-level marker for storage blocks (all modes).
 * Format: id:tier[:facing:FACING]
 */
public final class StorageMarker {
    private StorageMarker() {}

    private static final String PREFIX = "storage";

    public record Data(String storageId, StorageTier tier, BlockFace facing) {}

    public static void set(Plugin plugin, Block block, String storageId, StorageTier tier) {
        ChunkMarkerStore.setMarker(plugin, PREFIX, block, storageId + ":" + tier.key());
    }

    public static void set(Plugin plugin, Block block, String storageId, StorageTier tier, BlockFace facing) {
        if (facing == null) {
            set(plugin, block, storageId, tier);
            return;
        }
        ChunkMarkerStore.setMarker(plugin, PREFIX, block, storageId + ":" + tier.key() + ":facing:" + facing.name());
    }

    public static Optional<Data> get(Plugin plugin, Block block) {
        return ChunkMarkerStore.getMarker(plugin, PREFIX, block)
                .flatMap(raw -> {
                    String[] parts = raw.split(":");
                    if (parts.length < 2) return Optional.empty();
                    var tierOpt = StorageTier.fromString(parts[1]);
                    if (tierOpt.isEmpty()) return Optional.empty();
                    BlockFace facing = null;
                    if (parts.length >= 4 && "facing".equalsIgnoreCase(parts[2])) {
                        try {
                            facing = BlockFace.valueOf(parts[3]);
                        } catch (IllegalArgumentException ignored) {
                            facing = null;
                        }
                    }
                    return Optional.of(new Data(parts[0], tierOpt.get(), facing));
                });
    }

    public static void clear(Plugin plugin, Block block) {
        ChunkMarkerStore.clearMarker(plugin, PREFIX, block);
    }
}
