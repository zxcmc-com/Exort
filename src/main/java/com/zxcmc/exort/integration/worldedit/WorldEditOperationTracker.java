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
  private final Map<Long, Set<String>> operationPreservedStorageIds = new ConcurrentHashMap<>();
  private final Map<Long, Long> operationSeenMs = new ConcurrentHashMap<>();
  private final Map<BlockRef, LatestUpdate> latestUpdates = new ConcurrentHashMap<>();
  private final Map<String, LatestStorageUpdate> latestStorageUpdates = new ConcurrentHashMap<>();

  long nextOperationId() {
    return operationIds.incrementAndGet();
  }

  void record(MarkerUpdate update) {
    if (update == null) return;
    long now = System.currentTimeMillis();
    operationSeenMs.put(update.operationId(), now);
    BlockRef ref = new BlockRef(update.worldId(), update.x(), update.y(), update.z());
    latestUpdates.compute(
        ref,
        (ignored, current) -> {
          if (current == null
              || MarkerUpdate.hasHigherPriority(
                  update.operationId(),
                  update.authoritativeFinalState(),
                  current.operationId(),
                  current.authoritativeFinalState())) {
            return new LatestUpdate(update.operationId(), update.authoritativeFinalState(), now);
          }
          return new LatestUpdate(current.operationId(), current.authoritativeFinalState(), now);
        });
    String removedStorageId = update.removedStorageId();
    if (removedStorageId != null && !removedStorageId.isBlank()) {
      operationRemovedStorageIds
          .computeIfAbsent(update.operationId(), ignored -> ConcurrentHashMap.newKeySet())
          .add(removedStorageId);
    }
    MarkerSnapshot snapshot = update.snapshot();
    if (snapshot != null && snapshot.storage() != null) {
      String storageId = snapshot.storage().storageId();
      if (storageId != null && !storageId.isBlank()) {
        BlockRef destination = new BlockRef(update.worldId(), update.x(), update.y(), update.z());
        latestStorageUpdates.compute(
            storageId,
            (ignored, current) -> {
              if (current == null
                  || MarkerUpdate.hasHigherPriority(
                      update.operationId(),
                      update.authoritativeFinalState(),
                      current.operationId(),
                      current.authoritativeFinalState())) {
                return new LatestStorageUpdate(
                    update.operationId(), update.authoritativeFinalState(), destination, now);
              }
              return new LatestStorageUpdate(
                  current.operationId(),
                  current.authoritativeFinalState(),
                  current.destination(),
                  now);
            });
      }
    }
    if (update.moveOperation() && snapshot != null && snapshot.storage() != null) {
      String preservedStorageId = snapshot.storage().storageId();
      if (preservedStorageId != null && !preservedStorageId.isBlank()) {
        operationPreservedStorageIds
            .computeIfAbsent(update.operationId(), ignored -> ConcurrentHashMap.newKeySet())
            .add(preservedStorageId);
      }
    }
  }

  boolean isSuperseded(MarkerUpdate update) {
    if (update == null) return false;
    LatestUpdate latest =
        latestUpdates.get(new BlockRef(update.worldId(), update.x(), update.y(), update.z()));
    if (latest != null
        && MarkerUpdate.hasHigherPriority(
            latest.operationId(),
            latest.authoritativeFinalState(),
            update.operationId(),
            update.authoritativeFinalState())) {
      return true;
    }
    MarkerSnapshot snapshot = update.snapshot();
    if (snapshot == null || snapshot.storage() == null) {
      return false;
    }
    String storageId = snapshot.storage().storageId();
    if (storageId == null || storageId.isBlank()) {
      return false;
    }
    LatestStorageUpdate latestStorage = latestStorageUpdates.get(storageId);
    return latestStorage != null
        && MarkerUpdate.hasHigherPriority(
            latestStorage.operationId(),
            latestStorage.authoritativeFinalState(),
            update.operationId(),
            update.authoritativeFinalState());
  }

  boolean preservesStorageIdentity(MarkerUpdate update) {
    if (update == null || update.removedStorageId() == null) {
      return false;
    }
    if (update.moveOperation()) {
      return true;
    }
    Set<String> preserved = operationPreservedStorageIds.get(update.operationId());
    return preserved != null && preserved.contains(update.removedStorageId());
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
    for (Map.Entry<Long, Long> entry : operationSeenMs.entrySet()) {
      long lastSeenMs = entry.getValue();
      if (nowMs - lastSeenMs <= OPERATION_STORAGE_TTL_MS) {
        continue;
      }
      long operationId = entry.getKey();
      if (operationSeenMs.remove(operationId, lastSeenMs)) {
        operationRemovedStorageIds.remove(operationId);
        operationPreservedStorageIds.remove(operationId);
      }
    }
    latestUpdates
        .entrySet()
        .removeIf(entry -> nowMs - entry.getValue().seenMs() > OPERATION_STORAGE_TTL_MS);
    latestStorageUpdates
        .entrySet()
        .removeIf(entry -> nowMs - entry.getValue().seenMs() > OPERATION_STORAGE_TTL_MS);
  }

  void clear() {
    operationRemovedStorageIds.clear();
    operationPreservedStorageIds.clear();
    operationSeenMs.clear();
    latestUpdates.clear();
    latestStorageUpdates.clear();
  }

  private record LatestUpdate(long operationId, boolean authoritativeFinalState, long seenMs) {}

  private record LatestStorageUpdate(
      long operationId, boolean authoritativeFinalState, BlockRef destination, long seenMs) {}
}
