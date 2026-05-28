package com.zxcmc.exort.integration.worldedit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class WorldEditOperationTracker {
  private static final long OPERATION_STORAGE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

  private final AtomicLong operationIds = new AtomicLong();
  private final Map<Long, Set<String>> operationRemovedStorageIds = new ConcurrentHashMap<>();
  private final Map<Long, Long> operationStorageSeenMs = new ConcurrentHashMap<>();

  long nextOperationId() {
    return operationIds.incrementAndGet();
  }

  void record(MarkerUpdate update) {
    if (update == null) return;
    operationStorageSeenMs.put(update.operationId(), System.currentTimeMillis());
    String removedStorageId = update.removedStorageId();
    if (removedStorageId != null && !removedStorageId.isBlank()) {
      operationRemovedStorageIds
          .computeIfAbsent(update.operationId(), ignored -> ConcurrentHashMap.newKeySet())
          .add(removedStorageId);
    }
  }

  Map<Long, Set<String>> removedStorageIdsByOperation(Map<ChunkKey, ChunkUpdateBatch> batches) {
    Map<Long, Set<String>> removedStorageIdsByOperation = new HashMap<>();
    for (ChunkUpdateBatch batch : batches.values()) {
      for (PendingUpdate pending : batch.updates) {
        MarkerUpdate update = pending.update;
        Set<String> knownRemoved = operationRemovedStorageIds.get(update.operationId());
        if (knownRemoved != null && !knownRemoved.isEmpty()) {
          removedStorageIdsByOperation
              .computeIfAbsent(update.operationId(), ignored -> new HashSet<>())
              .addAll(knownRemoved);
        }
        String removedId = update.removedStorageId();
        if (removedId != null && !removedId.isBlank()) {
          removedStorageIdsByOperation
              .computeIfAbsent(update.operationId(), ignored -> new HashSet<>())
              .add(removedId);
        }
      }
    }
    return removedStorageIdsByOperation;
  }

  void purge(long nowMs) {
    for (Map.Entry<Long, Long> entry : operationStorageSeenMs.entrySet()) {
      long lastSeenMs = entry.getValue();
      if (nowMs - lastSeenMs <= OPERATION_STORAGE_TTL_MS) {
        continue;
      }
      long operationId = entry.getKey();
      if (operationStorageSeenMs.remove(operationId, lastSeenMs)) {
        operationRemovedStorageIds.remove(operationId);
      }
    }
  }

  void clear() {
    operationRemovedStorageIds.clear();
    operationStorageSeenMs.clear();
  }
}
