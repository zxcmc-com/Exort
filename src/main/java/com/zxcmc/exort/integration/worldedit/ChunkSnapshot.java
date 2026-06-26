package com.zxcmc.exort.integration.worldedit;

import java.util.Map;
import org.enginehub.linbus.tree.LinCompoundTag;

final class ChunkSnapshot {
  private final Map<Long, LinCompoundTag> data;

  private ChunkSnapshot(Map<Long, LinCompoundTag> data) {
    this.data = data == null || data.isEmpty() ? Map.of() : Map.copyOf(data);
  }

  static ChunkSnapshot empty() {
    return new ChunkSnapshot(Map.of());
  }

  static ChunkSnapshot of(Map<Long, LinCompoundTag> data) {
    return new ChunkSnapshot(data);
  }

  boolean isEmpty() {
    return data.isEmpty();
  }

  LinCompoundTag get(long key) {
    return data.get(key);
  }
}
