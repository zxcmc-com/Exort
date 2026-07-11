package com.zxcmc.exort.storage;

import java.util.Objects;
import java.util.UUID;

/** Durable ownership of one physical storage identity by one block position. */
public record StorageClaim(
    String storageId,
    UUID worldId,
    String worldKey,
    String worldName,
    int x,
    int y,
    int z,
    long claimedAt,
    long updatedAt) {
  public StorageClaim {
    if (storageId == null || storageId.isBlank()) {
      throw new IllegalArgumentException("storageId must not be blank");
    }
    Objects.requireNonNull(worldId, "worldId");
    worldKey = normalized(worldKey, "worldKey");
    worldName = normalized(worldName, "worldName");
  }

  public StorageClaimLocation location() {
    return new StorageClaimLocation(worldId, worldKey, worldName, x, y, z);
  }

  private static String normalized(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
