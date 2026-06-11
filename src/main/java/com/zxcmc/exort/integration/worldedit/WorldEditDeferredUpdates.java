package com.zxcmc.exort.integration.worldedit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class WorldEditDeferredUpdates {
  private final Map<ChunkKey, ChunkUpdateBatch> updatesByChunk = new ConcurrentHashMap<>();

  int defer(ChunkKey key, Iterable<PendingUpdate> updates) {
    if (key == null || updates == null) {
      return 0;
    }
    ChunkUpdateBatch batch = updatesByChunk.computeIfAbsent(key, ChunkUpdateBatch::new);
    int count = 0;
    for (PendingUpdate update : updates) {
      if (update == null) {
        continue;
      }
      batch.add(update);
      count++;
    }
    return count;
  }

  ChunkUpdateBatch remove(ChunkKey key) {
    return key == null ? null : updatesByChunk.remove(key);
  }

  int chunkCount() {
    return updatesByChunk.size();
  }

  int updateCount() {
    int total = 0;
    for (ChunkUpdateBatch batch : updatesByChunk.values()) {
      total += batch.updates.size();
    }
    return total;
  }

  void clear() {
    updatesByChunk.clear();
  }
}
