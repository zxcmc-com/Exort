package com.zxcmc.exort.integration.worldedit;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class ChunkUpdateBatch {
  final ChunkKey key;
  final Queue<PendingUpdate> updates = new ConcurrentLinkedQueue<>();

  ChunkUpdateBatch(ChunkKey key) {
    this.key = key;
  }

  void add(PendingUpdate update) {
    updates.add(update);
  }
}
