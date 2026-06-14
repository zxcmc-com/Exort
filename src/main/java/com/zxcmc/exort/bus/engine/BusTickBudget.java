package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.bus.BusPos;
import java.util.HashMap;
import java.util.Map;

final class BusTickBudget {
  private final int maxAttemptsPerTick;
  private final int maxAttemptsPerChunk;
  private final Map<ChunkKey, Integer> chunkAttempts = new HashMap<>();
  private int attempts;

  BusTickBudget(int maxAttemptsPerTick, int maxAttemptsPerChunk) {
    this.maxAttemptsPerTick = Math.max(1, maxAttemptsPerTick);
    this.maxAttemptsPerChunk = Math.max(0, maxAttemptsPerChunk);
  }

  boolean hasGlobalBudget() {
    return attempts < maxAttemptsPerTick;
  }

  void recordDueAttempt() {
    attempts++;
  }

  boolean isChunkBudgetReached(BusPos pos) {
    return maxAttemptsPerChunk > 0
        && chunkAttempts.getOrDefault(ChunkKey.from(pos), 0) >= maxAttemptsPerChunk;
  }

  void recordChunkAttempt(BusPos pos) {
    if (maxAttemptsPerChunk <= 0) {
      return;
    }
    ChunkKey key = ChunkKey.from(pos);
    chunkAttempts.put(key, chunkAttempts.getOrDefault(key, 0) + 1);
  }

  private record ChunkKey(java.util.UUID world, int x, int z) {
    static ChunkKey from(BusPos pos) {
      return new ChunkKey(pos.world(), pos.x() >> 4, pos.z() >> 4);
    }
  }
}
