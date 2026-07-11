package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import java.util.UUID;

record ChunkLoaderData(
    UUID id,
    ChunkLoaderType type,
    UUID placedByUuid,
    String placedByName,
    long createdAt,
    boolean enabled,
    boolean bypassLimits) {
  ChunkLoaderData {
    type = type == null ? ChunkLoaderType.defaultType() : type;
  }

  ChunkLoaderData(
      UUID id, ChunkLoaderType type, UUID placedByUuid, String placedByName, long createdAt) {
    this(id, type, placedByUuid, placedByName, createdAt, true, false);
  }

  ChunkLoaderData(
      UUID id,
      ChunkLoaderType type,
      UUID placedByUuid,
      String placedByName,
      long createdAt,
      boolean enabled) {
    this(id, type, placedByUuid, placedByName, createdAt, enabled, false);
  }
}
