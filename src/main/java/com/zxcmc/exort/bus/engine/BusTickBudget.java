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

  void reset() {
    attempts = 0;
    chunkAttempts.clear();
  }

  boolean hasGlobalBudget() {
    return attempts < maxAttemptsPerTick;
  }

  void recordDueAttempt() {
    attempts++;
  }

  boolean tryRecordChunkAttempt(BusPos pos) {
    if (maxAttemptsPerChunk <= 0) {
      return true;
    }
    ChunkKey key = ChunkKey.from(pos);
    int attemptsInChunk = chunkAttempts.getOrDefault(key, 0);
    if (attemptsInChunk >= maxAttemptsPerChunk) {
      return false;
    }
    chunkAttempts.put(key, attemptsInChunk + 1);
    return true;
  }

  private record ChunkKey(java.util.UUID world, int x, int z) {
    static ChunkKey from(BusPos pos) {
      return new ChunkKey(pos.world(), pos.x() >> 4, pos.z() >> 4);
    }
  }
}
