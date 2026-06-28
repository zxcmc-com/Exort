package com.zxcmc.exort.marker;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class ChunkLoaderMarker {
  private static final String SECTION = "chunk_loader";
  private static final String FIELD_ID = "id";
  private static final String FIELD_PLACED_BY_UUID = "placed_by_uuid";
  private static final String FIELD_PLACED_BY_NAME = "placed_by_name";
  private static final String FIELD_CREATED_AT = "created_at";

  private ChunkLoaderMarker() {}

  public record Data(UUID id, UUID placedByUuid, String placedByName, long createdAt) {}

  public static void set(
      Plugin plugin, Block block, UUID id, UUID placedByUuid, String placedByName, long createdAt) {
    if (id == null) return;
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_ID, id.toString());
    if (placedByUuid != null) {
      ChunkMarkerStore.setString(
          plugin, block, SECTION, FIELD_PLACED_BY_UUID, placedByUuid.toString());
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_PLACED_BY_UUID);
    }
    if (placedByName != null && !placedByName.isBlank()) {
      ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_PLACED_BY_NAME, placedByName);
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_PLACED_BY_NAME);
    }
    if (createdAt > 0L) {
      ChunkMarkerStore.setLong(plugin, block, SECTION, FIELD_CREATED_AT, createdAt);
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_CREATED_AT);
    }
  }

  public static Optional<Data> get(Plugin plugin, Block block) {
    String idRaw = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_ID).orElse(null);
    if (idRaw == null || idRaw.isBlank()) {
      return Optional.empty();
    }
    UUID id;
    try {
      id = UUID.fromString(idRaw);
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
    UUID placedByUuid = null;
    String placedByRaw =
        ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_PLACED_BY_UUID).orElse(null);
    if (placedByRaw != null) {
      try {
        placedByUuid = UUID.fromString(placedByRaw);
      } catch (IllegalArgumentException ignored) {
        placedByUuid = null;
      }
    }
    String placedByName =
        ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_PLACED_BY_NAME).orElse(null);
    long createdAt = ChunkMarkerStore.getLong(plugin, block, SECTION, FIELD_CREATED_AT).orElse(0L);
    return Optional.of(new Data(id, placedByUuid, placedByName, createdAt));
  }

  public static boolean isChunkLoader(Plugin plugin, Block block) {
    return get(plugin, block).isPresent();
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }
}
