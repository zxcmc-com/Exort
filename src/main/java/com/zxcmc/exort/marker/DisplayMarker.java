package com.zxcmc.exort.marker;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/** Tracks spawned ItemDisplay UUIDs for resource-mode blocks to clean up / update after restart. */
public final class DisplayMarker {
  private DisplayMarker() {}

  private static final String SECTION = "display";

  public static void set(Plugin plugin, String prefix, Block block, UUID uuid) {
    if (uuid == null) return;
    ChunkMarkerStore.setString(plugin, block, SECTION, prefix, uuid.toString());
  }

  public static Optional<UUID> get(Plugin plugin, String prefix, Block block) {
    String raw = ChunkMarkerStore.getString(plugin, block, SECTION, prefix).orElse(null);
    if (raw == null) return Optional.empty();
    try {
      return Optional.of(UUID.fromString(raw));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public static void clear(Plugin plugin, String prefix, Block block) {
    ChunkMarkerStore.removeField(plugin, block, SECTION, prefix);
  }

  public static void clear(Plugin plugin, String prefix, Chunk chunk) {
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          if (!ChunkMarkerStore.getString(plugin, block, SECTION, prefix).isPresent()) return;
          ChunkMarkerStore.removeField(plugin, block, SECTION, prefix);
        });
  }
}
