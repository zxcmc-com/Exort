package com.zxcmc.exort.integration.worldedit;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Bounded, deduplicating coordinate queue for fail-closed direct-mutation reconciliation. */
final class WorldEditDirectReconciliationQueue {
  enum ReserveResult {
    ADDED,
    ALREADY_PRESENT,
    FULL
  }

  private final int capacity;
  private final Queue<BlockRef> entries = new ConcurrentLinkedQueue<>();
  private final Set<BlockRef> keys = ConcurrentHashMap.newKeySet();

  WorldEditDirectReconciliationQueue(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.capacity = capacity;
  }

  ReserveResult reserve(BlockRef ref) {
    if (ref == null) {
      throw new IllegalArgumentException("ref must not be null");
    }
    if (keys.contains(ref)) {
      return ReserveResult.ALREADY_PRESENT;
    }
    synchronized (this) {
      if (keys.contains(ref)) {
        return ReserveResult.ALREADY_PRESENT;
      }
      if (keys.size() >= capacity) {
        return ReserveResult.FULL;
      }
      keys.add(ref);
      entries.add(ref);
      return ReserveResult.ADDED;
    }
  }

  BlockRef poll() {
    BlockRef ref = entries.poll();
    if (ref != null) {
      keys.remove(ref);
    }
    return ref;
  }

  boolean contains(BlockRef ref) {
    return keys.contains(ref);
  }

  boolean isEmpty() {
    return entries.isEmpty();
  }

  int size() {
    return keys.size();
  }

  void clear() {
    entries.clear();
    keys.clear();
  }
}
