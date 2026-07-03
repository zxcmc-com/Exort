package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import java.util.UUID;

record ChunkLoaderData(
    UUID id,
    ChunkLoaderType type,
    UUID placedByUuid,
    String placedByName,
    long createdAt,
    boolean enabled) {
  ChunkLoaderData {
    type = type == null ? ChunkLoaderType.defaultType() : type;
  }

  ChunkLoaderData(
      UUID id, ChunkLoaderType type, UUID placedByUuid, String placedByName, long createdAt) {
    this(id, type, placedByUuid, placedByName, createdAt, true);
  }
}
