package com.zxcmc.exort.chunkloader;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread-confined operation tokens for asynchronous Chunk Loader persistence callbacks. */
final class ChunkLoaderPersistenceGuard {
  private final Map<UUID, Long> pending = new HashMap<>();
  private long nextOperation;

  long begin(UUID loaderId) {
    long operation = ++nextOperation;
    pending.put(loaderId, operation);
    return operation;
  }

  boolean complete(UUID loaderId, long operation) {
    return pending.remove(loaderId, operation);
  }

  void cancel(UUID loaderId) {
    pending.remove(loaderId);
  }

  void clear() {
    pending.clear();
  }
}
