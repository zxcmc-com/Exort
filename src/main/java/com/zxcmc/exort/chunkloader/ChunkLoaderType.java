package com.zxcmc.exort.chunkloader;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ChunkLoaderType {
  CHUNK_LOADER("chunk_loader", "item.chunk_loader"),
  PERSONAL_CHUNK_LOADER("personal_chunk_loader", "item.personal_chunk_loader"),
  DORMANT_CHUNK_LOADER("dormant_chunk_loader", "item.dormant_chunk_loader");

  private static final List<ChunkLoaderType> VALUES = List.of(values());

  private final String id;
  private final String translationKey;

  ChunkLoaderType(String id, String translationKey) {
    this.id = id;
    this.translationKey = translationKey;
  }

  public String id() {
    return id;
  }

  public String translationKey() {
    return translationKey;
  }

  public static List<ChunkLoaderType> all() {
    return VALUES;
  }

  public static ChunkLoaderType defaultType() {
    return CHUNK_LOADER;
  }

  public static boolean isChunkLoaderId(String raw) {
    return fromId(raw).isPresent();
  }

  public static Optional<ChunkLoaderType> fromId(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (ChunkLoaderType type : VALUES) {
      if (type.id.equals(normalized)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  public static Optional<ChunkLoaderType> fromNullableId(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.of(defaultType());
    }
    return fromId(raw);
  }
}
