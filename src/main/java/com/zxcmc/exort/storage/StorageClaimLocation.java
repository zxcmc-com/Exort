package com.zxcmc.exort.storage;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.block.Block;

public record StorageClaimLocation(
    UUID worldId, String worldKey, String worldName, int x, int y, int z) {
  public StorageClaimLocation {
    Objects.requireNonNull(worldId, "worldId");
    if (worldKey == null || worldKey.isBlank()) {
      throw new IllegalArgumentException("worldKey must not be blank");
    }
    if (worldName == null || worldName.isBlank()) {
      throw new IllegalArgumentException("worldName must not be blank");
    }
    worldKey = worldKey.trim();
    worldName = worldName.trim();
  }

  public static StorageClaimLocation fromBlock(Block block) {
    Objects.requireNonNull(block, "block");
    var world = block.getWorld();
    return new StorageClaimLocation(
        world.getUID(),
        world.getKey().asString(),
        world.getName(),
        block.getX(),
        block.getY(),
        block.getZ());
  }

  /** World display metadata may change; physical identity is UUID plus coordinates. */
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof StorageClaimLocation that)) return false;
    return x == that.x && y == that.y && z == that.z && worldId.equals(that.worldId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(worldId, x, y, z);
  }
}
