package com.zxcmc.exort.chunkloader;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;

public record ChunkLoaderObservation(
    UUID id,
    ChunkLoaderRegistryStatus status,
    UUID worldId,
    String worldKey,
    String worldName,
    double x,
    double y,
    double z,
    UUID actorUuid,
    String actorName,
    String source,
    String reason,
    long observedAt) {
  public ChunkLoaderObservation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(worldId, "worldId");
    worldKey = worldKey == null || worldKey.isBlank() ? worldId.toString() : worldKey;
    worldName = worldName == null || worldName.isBlank() ? worldKey : worldName;
    actorName = actorName == null || actorName.isBlank() ? null : actorName;
    source = source == null || source.isBlank() ? null : source;
    reason = reason == null || reason.isBlank() ? null : reason;
    observedAt = observedAt <= 0L ? Instant.now().getEpochSecond() : observedAt;
  }

  public static ChunkLoaderObservation atLocation(
      UUID id,
      ChunkLoaderRegistryStatus status,
      Location location,
      Player actor,
      String source,
      String reason) {
    Location safeLocation = location == null && actor != null ? actor.getLocation() : location;
    World world = safeLocation == null ? null : safeLocation.getWorld();
    if (world == null) {
      return null;
    }
    NamespacedKey key = world.getKey();
    return new ChunkLoaderObservation(
        id,
        status,
        world.getUID(),
        key == null ? world.getUID().toString() : key.toString(),
        world.getName(),
        safeLocation.getX(),
        safeLocation.getY(),
        safeLocation.getZ(),
        actor == null ? null : actor.getUniqueId(),
        actor == null ? null : actor.getName(),
        source,
        reason,
        Instant.now().getEpochSecond());
  }

  public static ChunkLoaderObservation fromRecord(
      ChunkLoaderRecord record,
      ChunkLoaderRegistryStatus status,
      Player actor,
      String source,
      String reason) {
    if (record == null) {
      return null;
    }
    return new ChunkLoaderObservation(
        record.id(),
        status,
        record.worldId(),
        record.worldKey(),
        record.worldName(),
        record.x() + 0.5D,
        record.y() + 1.0D,
        record.z() + 0.5D,
        actor == null ? null : actor.getUniqueId(),
        actor == null ? null : actor.getName(),
        source,
        reason,
        Instant.now().getEpochSecond());
  }
}
