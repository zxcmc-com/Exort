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
    ChunkLoaderType type,
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
    boolean enabled,
    boolean bypassLimits,
    long createdAt,
    long updatedAt) {
  public ChunkLoaderRecord {
    Objects.requireNonNull(id, "id");
    type = type == null ? ChunkLoaderType.defaultType() : type;
    Objects.requireNonNull(worldId, "worldId");
    worldKey = worldKey == null || worldKey.isBlank() ? worldId.toString() : worldKey;
    worldName = worldName == null || worldName.isBlank() ? worldKey : worldName;
    placedByName = placedByName == null || placedByName.isBlank() ? "unknown" : placedByName;
  }

  public ChunkLoaderRecord(
      UUID id,
      ChunkLoaderType type,
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
      boolean enabled,
      long createdAt,
      long updatedAt) {
    this(
        id,
        type,
        worldId,
        worldKey,
        worldName,
        x,
        y,
        z,
        chunkX,
        chunkZ,
        placedByUuid,
        placedByName,
        radius,
        enabled,
        false,
        createdAt,
        updatedAt);
  }

  public static ChunkLoaderRecord placed(Block block, UUID id, Player player, int radius) {
    return placed(block, id, player, radius, ChunkLoaderType.defaultType());
  }

  public static ChunkLoaderRecord placed(
      Block block, UUID id, Player player, int radius, ChunkLoaderType type) {
    return placed(block, id, player, radius, type, false);
  }

  public static ChunkLoaderRecord placed(
      Block block, UUID id, Player player, int radius, ChunkLoaderType type, boolean bypassLimits) {
    long now = Instant.now().getEpochSecond();
    return fromBlock(
        block,
        id,
        type,
        player == null ? null : player.getUniqueId(),
        player == null ? null : player.getName(),
        radius,
        true,
        bypassLimits,
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
    return fromBlock(
        block,
        id,
        ChunkLoaderType.defaultType(),
        placedByUuid,
        placedByName,
        radius,
        true,
        createdAt,
        updatedAt);
  }

  public static ChunkLoaderRecord fromBlock(
      Block block,
      UUID id,
      ChunkLoaderType type,
      UUID placedByUuid,
      String placedByName,
      int radius,
      long createdAt,
      long updatedAt) {
    return fromBlock(
        block, id, type, placedByUuid, placedByName, radius, true, createdAt, updatedAt);
  }

  public static ChunkLoaderRecord fromBlock(
      Block block,
      UUID id,
      ChunkLoaderType type,
      UUID placedByUuid,
      String placedByName,
      int radius,
      boolean enabled,
      long createdAt,
      long updatedAt) {
    return fromBlock(
        block, id, type, placedByUuid, placedByName, radius, enabled, false, createdAt, updatedAt);
  }

  public static ChunkLoaderRecord fromBlock(
      Block block,
      UUID id,
      ChunkLoaderType type,
      UUID placedByUuid,
      String placedByName,
      int radius,
      boolean enabled,
      boolean bypassLimits,
      long createdAt,
      long updatedAt) {
    Objects.requireNonNull(block, "block");
    World world = Objects.requireNonNull(block.getWorld(), "world");
    NamespacedKey key = world.getKey();
    return new ChunkLoaderRecord(
        id,
        type,
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
        enabled,
        bypassLimits,
        createdAt,
        updatedAt);
  }

  public ChunkLoaderRecord withEnabled(boolean enabled, long updatedAt) {
    return new ChunkLoaderRecord(
        id,
        type,
        worldId,
        worldKey,
        worldName,
        x,
        y,
        z,
        chunkX,
        chunkZ,
        placedByUuid,
        placedByName,
        radius,
        enabled,
        bypassLimits,
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
