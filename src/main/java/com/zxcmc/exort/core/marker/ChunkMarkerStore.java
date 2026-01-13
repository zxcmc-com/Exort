package com.zxcmc.exort.core.marker;

import java.util.Optional;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Generic chunk-level marker store for resource-mode blocks (wire/storage/terminal). */
public final class ChunkMarkerStore {
  private ChunkMarkerStore() {}

  private static NamespacedKey key(Plugin plugin, String prefix, Block block) {
    return new NamespacedKey(
        plugin, prefix + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
  }

  public static void setMarker(Plugin plugin, String prefix, Block block, String value) {
    Chunk chunk = block.getChunk();
    PersistentDataContainer pdc = chunk.getPersistentDataContainer();
    pdc.set(key(plugin, prefix, block), PersistentDataType.STRING, value);
  }

  public static Optional<String> getMarker(Plugin plugin, String prefix, Block block) {
    Chunk chunk = block.getChunk();
    PersistentDataContainer pdc = chunk.getPersistentDataContainer();
    String raw = pdc.get(key(plugin, prefix, block), PersistentDataType.STRING);
    return Optional.ofNullable(raw);
  }

  public static void clearMarker(Plugin plugin, String prefix, Block block) {
    Chunk chunk = block.getChunk();
    PersistentDataContainer pdc = chunk.getPersistentDataContainer();
    pdc.remove(key(plugin, prefix, block));
  }
}
