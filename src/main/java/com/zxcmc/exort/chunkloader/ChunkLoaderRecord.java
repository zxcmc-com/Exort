package com.zxcmc.exort.chunkloader;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public record ChunkLoaderRecord(
    UUID id,
    UUID worldId,
    String worldKey,
    String worldName,
    int x,
    int y,
    int z,
    int chunkX,
    int chunkZ,
    UUID placedByUuid,
    String placedByName,
    int radius,
    long createdAt,
    long updatedAt) {
  public ChunkLoaderRecord {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(worldId, "worldId");
    worldKey = worldKey == null || worldKey.isBlank() ? worldId.toString() : worldKey;
    worldName = worldName == null || worldName.isBlank() ? worldKey : worldName;
    placedByName = placedByName == null || placedByName.isBlank() ? "unknown" : placedByName;
  }

  public static ChunkLoaderRecord placed(Block block, UUID id, Player player, int radius) {
    long now = Instant.now().getEpochSecond();
    return fromBlock(
        block,
        id,
        player == null ? null : player.getUniqueId(),
        player == null ? null : player.getName(),
        radius,
        now,
        now);
  }

  public static ChunkLoaderRecord fromBlock(
      Block block,
      UUID id,
      UUID placedByUuid,
      String placedByName,
      int radius,
      long createdAt,
      long updatedAt) {
    Objects.requireNonNull(block, "block");
    World world = Objects.requireNonNull(block.getWorld(), "world");
    NamespacedKey key = world.getKey();
    return new ChunkLoaderRecord(
        id,
        world.getUID(),
        key == null ? world.getUID().toString() : key.toString(),
        world.getName(),
        block.getX(),
        block.getY(),
        block.getZ(),
        block.getX() >> 4,
        block.getZ() >> 4,
        placedByUuid,
        placedByName,
        radius,
        createdAt,
        updatedAt);
  }

  public boolean sameBlock(Block block) {
    return block != null
        && block.getWorld() != null
        && worldId.equals(block.getWorld().getUID())
        && x == block.getX()
        && y == block.getY()
        && z == block.getZ();
  }
}
