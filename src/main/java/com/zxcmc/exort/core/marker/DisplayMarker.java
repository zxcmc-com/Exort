package com.zxcmc.exort.core.marker;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Tracks spawned ItemDisplay UUIDs for resource-mode blocks to clean up / update after restart. */
public final class DisplayMarker {
  private DisplayMarker() {}

  private static NamespacedKey key(Plugin plugin, String prefix, Block block) {
    return new NamespacedKey(
        plugin, prefix + "_display_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
  }

  public static void set(Plugin plugin, String prefix, Block block, UUID uuid) {
    Chunk chunk = block.getChunk();
    PersistentDataContainer pdc = chunk.getPersistentDataContainer();
    pdc.set(key(plugin, prefix, block), PersistentDataType.STRING, uuid.toString());
  }

  public static Optional<UUID> get(Plugin plugin, String prefix, Block block) {
    Chunk chunk = block.getChunk();
    PersistentDataContainer pdc = chunk.getPersistentDataContainer();
    String raw = pdc.get(key(plugin, prefix, block), PersistentDataType.STRING);
    if (raw == null) return Optional.empty();
    try {
      return Optional.of(UUID.fromString(raw));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public static void clear(Plugin plugin, String prefix, Block block) {
    Chunk chunk = block.getChunk();
    PersistentDataContainer pdc = chunk.getPersistentDataContainer();
    pdc.remove(key(plugin, prefix, block));
  }

  public static void clear(Plugin plugin, String prefix, Chunk chunk) {
    PersistentDataContainer pdc = chunk.getPersistentDataContainer();
    for (NamespacedKey k : pdc.getKeys()) {
      if (k.getNamespace().equals(plugin.getName().toLowerCase())
          && k.getKey().startsWith(prefix + "_display_")) {
        pdc.remove(k);
      }
    }
  }
}
