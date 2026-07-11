package com.zxcmc.exort.wireless.transmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sparse chunk buckets for bounded transmitter coverage lookups. */
final class TransmitterSpatialIndex {
  record Position(UUID worldId, int x, int y, int z) {}

  private record ChunkKey(UUID worldId, int x, int z) {}

  private final Set<Position> positions = ConcurrentHashMap.newKeySet();
  private final Map<ChunkKey, Set<Position>> byChunk = new ConcurrentHashMap<>();

  void add(Position position) {
    if (position == null || !positions.add(position)) return;
    byChunk
        .computeIfAbsent(chunkKey(position), ignored -> ConcurrentHashMap.newKeySet())
        .add(position);
  }

  void remove(Position position) {
    if (position == null || !positions.remove(position)) return;
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
    if (removed != null) positions.removeAll(removed);
  }

  List<Position> candidates(UUID worldId, int blockX, int blockZ, int rangeBlocks) {
    if (worldId == null || positions.isEmpty()) return List.of();
    int radiusChunks = (int) ((Math.max(0L, (long) rangeBlocks) + 15L) >> 4);
    int centerX = blockX >> 4;
    int centerZ = blockZ >> 4;
    long width = (long) radiusChunks * 2L + 1L;
    long bucketLookups = width * width;
    if (bucketLookups > positions.size()) {
      return positions.stream().filter(pos -> worldId.equals(pos.worldId())).toList();
    }
    List<Position> result = new ArrayList<>();
    for (int chunkX = centerX - radiusChunks; chunkX <= centerX + radiusChunks; chunkX++) {
      for (int chunkZ = centerZ - radiusChunks; chunkZ <= centerZ + radiusChunks; chunkZ++) {
        Set<Position> bucket = byChunk.get(new ChunkKey(worldId, chunkX, chunkZ));
        if (bucket != null) result.addAll(bucket);
      }
    }
    return List.copyOf(result);
  }

  void clear() {
    positions.clear();
    byChunk.clear();
  }

  int size() {
    return positions.size();
  }

  private static ChunkKey chunkKey(Position position) {
    return new ChunkKey(position.worldId(), position.x() >> 4, position.z() >> 4);
  }
}
