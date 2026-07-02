package com.zxcmc.exort.chunkloader;

import java.util.Objects;
import java.util.UUID;

public record ChunkLoaderRegistryRecord(
    UUID id,
    ChunkLoaderType type,
    ChunkLoaderRegistryStatus status,
    UUID placedByUuid,
    String placedByName,
    UUID firstWorldId,
    String firstWorldKey,
    String firstWorldName,
    int firstX,
    int firstY,
    int firstZ,
    int firstChunkX,
    int firstChunkZ,
    UUID lastPlacedWorldId,
    String lastPlacedWorldKey,
    String lastPlacedWorldName,
    int lastPlacedX,
    int lastPlacedY,
    int lastPlacedZ,
    int lastPlacedChunkX,
    int lastPlacedChunkZ,
    UUID lastSeenWorldId,
    String lastSeenWorldKey,
    String lastSeenWorldName,
    Double lastSeenX,
    Double lastSeenY,
    Double lastSeenZ,
    UUID lastActorUuid,
    String lastActorName,
    String lastSource,
    String lastReason,
    long createdAt,
    long updatedAt,
    Long lastSeenAt) {
  public ChunkLoaderRegistryRecord {
    Objects.requireNonNull(id, "id");
    type = type == null ? ChunkLoaderType.defaultType() : type;
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(firstWorldId, "firstWorldId");
    Objects.requireNonNull(lastPlacedWorldId, "lastPlacedWorldId");
    firstWorldKey =
        firstWorldKey == null || firstWorldKey.isBlank() ? firstWorldId.toString() : firstWorldKey;
    firstWorldName =
        firstWorldName == null || firstWorldName.isBlank() ? firstWorldKey : firstWorldName;
    lastPlacedWorldKey =
        lastPlacedWorldKey == null || lastPlacedWorldKey.isBlank()
            ? lastPlacedWorldId.toString()
            : lastPlacedWorldKey;
    lastPlacedWorldName =
        lastPlacedWorldName == null || lastPlacedWorldName.isBlank()
            ? lastPlacedWorldKey
            : lastPlacedWorldName;
    placedByName = placedByName == null || placedByName.isBlank() ? "unknown" : placedByName;
    lastActorName = lastActorName == null || lastActorName.isBlank() ? null : lastActorName;
    lastSource = lastSource == null || lastSource.isBlank() ? null : lastSource;
    lastReason = lastReason == null || lastReason.isBlank() ? null : lastReason;
  }

  public boolean hasLastSeenLocation() {
    return lastSeenWorldId != null && lastSeenX != null && lastSeenY != null && lastSeenZ != null;
  }
}
