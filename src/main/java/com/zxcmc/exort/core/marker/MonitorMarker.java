package com.zxcmc.exort.core.marker;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Chunk-level marker for monitor blocks (all modes).
 */
public final class MonitorMarker {
    private MonitorMarker() {
    }

    private static final String PREFIX = "monitor";
    private static final String KEY_FACING = "facing";

    public static void set(Plugin plugin, Block block, BlockFace facing) {
        BlockFace safeFacing = facing == null ? BlockFace.SOUTH : facing;
        ChunkMarkerStore.setMarker(plugin, PREFIX, block, KEY_FACING + ":" + safeFacing.name());
    }

    public static boolean isMonitor(Plugin plugin, Block block) {
        return ChunkMarkerStore.getMarker(plugin, PREFIX, block).isPresent();
    }

    public static Optional<BlockFace> facing(Plugin plugin, Block block) {
        return ChunkMarkerStore.getMarker(plugin, PREFIX, block)
                .flatMap(raw -> {
                    if (raw == null) return Optional.empty();
                    for (String part : raw.split(";")) {
                        if (!part.startsWith(KEY_FACING + ":")) continue;
                        String dir = part.substring((KEY_FACING + ":").length());
                        try {
                            return Optional.of(BlockFace.valueOf(dir));
                        } catch (IllegalArgumentException ignored) {
                            return Optional.empty();
                        }
                    }
                    return Optional.empty();
                });
    }

    public static void setItem(Plugin plugin, Block block, String itemKey, byte[] itemBlob) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        if (itemKey != null) {
            pdc.set(itemKeyKey(plugin, block), PersistentDataType.STRING, itemKey);
        }
        if (itemBlob != null) {
            pdc.set(itemBlobKey(plugin, block), PersistentDataType.BYTE_ARRAY, itemBlob);
        }
    }

    public static Optional<String> itemKey(Plugin plugin, Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        String raw = pdc.get(itemKeyKey(plugin, block), PersistentDataType.STRING);
        return Optional.ofNullable(raw);
    }

    public static Optional<byte[]> itemBlob(Plugin plugin, Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        byte[] raw = pdc.get(itemBlobKey(plugin, block), PersistentDataType.BYTE_ARRAY);
        return Optional.ofNullable(raw);
    }

    public static void clearItem(Plugin plugin, Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        pdc.remove(itemKeyKey(plugin, block));
        pdc.remove(itemBlobKey(plugin, block));
    }

    public static void clear(Plugin plugin, Block block) {
        ChunkMarkerStore.clearMarker(plugin, PREFIX, block);
        clearItem(plugin, block);
    }

    private static NamespacedKey itemKeyKey(Plugin plugin, Block block) {
        return new NamespacedKey(plugin, "monitor_item_key_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }

    private static NamespacedKey itemBlobKey(Plugin plugin, Block block) {
        return new NamespacedKey(plugin, "monitor_item_blob_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }
}
