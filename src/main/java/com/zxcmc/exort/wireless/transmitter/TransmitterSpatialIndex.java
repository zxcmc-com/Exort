package com.zxcmc.exort.wireless.transmitter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Sparse chunk buckets for bounded transmitter coverage lookups. */
final class TransmitterSpatialIndex {
  record Position(UUID worldId, int x, int y, int z) {}

  record VisitResult(int examined, boolean matched) {}

  private record ChunkKey(UUID worldId, int x, int z) {}

  private final Set<Position> positions = ConcurrentHashMap.newKeySet();
  private final Map<ChunkKey, Set<Position>> byChunk = new ConcurrentHashMap<>();
  private final Map<UUID, Set<Position>> globalByWorld = new ConcurrentHashMap<>();

  void add(Position position) {
    add(position, false);
  }

  void add(Position position, boolean global) {
    if (position == null) return;
    positions.add(position);
    byChunk
        .computeIfAbsent(chunkKey(position), ignored -> ConcurrentHashMap.newKeySet())
        .add(position);
    if (global) {
      globalByWorld
          .computeIfAbsent(position.worldId(), ignored -> ConcurrentHashMap.newKeySet())
          .add(position);
    } else {
      removeGlobal(position);
    }
  }

  void remove(Position position) {
    if (position == null || !positions.remove(position)) return;
    removeGlobal(position);
    ChunkKey key = chunkKey(position);
    byChunk.computeIfPresent(
        key,
        (ignored, bucket) -> {
          bucket.remove(position);
          return bucket.isEmpty() ? null : bucket;
        });
  }

  void removeChunk(UUID worldId, int chunkX, int chunkZ) {
    Set<Position> removed = byChunk.remove(new ChunkKey(worldId, chunkX, chunkZ));
    if (removed != null) {
      positions.removeAll(removed);
      removed.forEach(this::removeGlobal);
    }
  }

  VisitResult visitCandidates(
      UUID worldId, int blockX, int blockZ, int rangeBlocks, Predicate<Position> visitor) {
    if (worldId == null || positions.isEmpty()) return new VisitResult(0, false);
    int radiusChunks = (int) ((Math.max(0L, (long) rangeBlocks) + 15L) >> 4);
    int centerX = blockX >> 4;
    int centerZ = blockZ >> 4;
    long width = (long) radiusChunks * 2L + 1L;
    long bucketLookups = width * width;
    if (bucketLookups > positions.size()) {
      return visitSet(positions, worldId, visitor);
    }
    int examined = 0;
    for (int chunkX = centerX - radiusChunks; chunkX <= centerX + radiusChunks; chunkX++) {
      for (int chunkZ = centerZ - radiusChunks; chunkZ <= centerZ + radiusChunks; chunkZ++) {
        Set<Position> bucket = byChunk.get(new ChunkKey(worldId, chunkX, chunkZ));
        if (bucket == null) continue;
        for (Position position : bucket) {
          examined++;
          if (visitor.test(position)) {
            return new VisitResult(examined, true);
          }
        }
      }
    }
    return new VisitResult(examined, false);
  }

  VisitResult visitGlobal(UUID worldId, Predicate<Position> visitor) {
    Set<Position> global = globalByWorld.get(worldId);
    return global == null ? new VisitResult(0, false) : visitSet(global, worldId, visitor);
  }

  void clear() {
    positions.clear();
    byChunk.clear();
    globalByWorld.clear();
  }

  int size() {
    return positions.size();
  }

  private static VisitResult visitSet(
      Set<Position> source, UUID worldId, Predicate<Position> visitor) {
    int examined = 0;
    for (Position position : source) {
      if (!worldId.equals(position.worldId())) continue;
      examined++;
      if (visitor.test(position)) {
        return new VisitResult(examined, true);
      }
    }
    return new VisitResult(examined, false);
  }

  private static ChunkKey chunkKey(Position position) {
    return new ChunkKey(position.worldId(), position.x() >> 4, position.z() >> 4);
  }

  private void removeGlobal(Position position) {
    globalByWorld.computeIfPresent(
        position.worldId(),
        (ignored, bucket) -> {
          bucket.remove(position);
          return bucket.isEmpty() ? null : bucket;
        });
  }
}
