package com.zxcmc.exort.display.refresh;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Main-thread-confined, resumable component traversal for sealed refresh batches. */
final class NetworkRefreshWorkQueue<N> {
  interface Topology<N> {
    boolean isNode(N node);

    void forEachConnectedNode(N node, Consumer<N> consumer);

    void enqueueRefresh(N node);
  }

  record DrainResult(
      int examined,
      int refreshes,
      int componentsStarted,
      int componentsCompleted,
      int skippedStarts,
      int overflowedBatches,
      int pendingStarts,
      int pendingWork) {}

  private final Map<Long, Batch<N>> batches = new LinkedHashMap<>();
  private long nextBatchId = 1L;

  long openBatch() {
    long id = nextBatchId++;
    batches.put(id, new Batch<>());
    return id;
  }

  boolean addStart(long batchId, N start) {
    Batch<N> batch = batches.get(batchId);
    return batch != null && !batch.sealed && start != null && batch.addStart(start);
  }

  boolean seal(long batchId) {
    Batch<N> batch = batches.get(batchId);
    if (batch == null || batch.sealed) {
      return false;
    }
    batch.sealed = true;
    return true;
  }

  void cancel(long batchId) {
    batches.remove(batchId);
  }

  DrainResult drain(int maxWork, int hardCap, Topology<N> topology) {
    int budget = Math.max(0, maxWork);
    int safeHardCap = Math.max(1, hardCap);
    int examined = 0;
    int refreshes = 0;
    int componentsStarted = 0;
    int componentsCompleted = 0;
    int skippedStarts = 0;
    int overflowedBatches = 0;
    while (examined < budget) {
      Map.Entry<Long, Batch<N>> batchEntry = firstSealedBatch();
      if (batchEntry == null) {
        break;
      }
      Batch<N> batch = batchEntry.getValue();
      if (!batch.frontier.isEmpty()) {
        N node = batch.frontier.removeFirst();
        examined++;
        topology.enqueueRefresh(node);
        refreshes++;
        topology.forEachConnectedNode(
            node,
            connected -> {
              if (batch.overflowed || batch.covered.contains(connected)) {
                return;
              }
              if (batch.componentNodes >= safeHardCap) {
                batch.overflowed = true;
                return;
              }
              batch.covered.add(connected);
              batch.frontier.addLast(connected);
              batch.componentNodes++;
            });
        if (batch.overflowed) {
          batch.clearPending();
          overflowedBatches++;
          batches.remove(batchEntry.getKey());
        } else if (batch.frontier.isEmpty()) {
          batch.componentNodes = 0;
          componentsCompleted++;
        }
        continue;
      }
      if (!batch.seeds.isEmpty()) {
        N seed = batch.seeds.removeFirst();
        examined++;
        if (batch.covered.contains(seed) || !topology.isNode(seed)) {
          skippedStarts++;
          continue;
        }
        batch.covered.add(seed);
        batch.frontier.addLast(seed);
        batch.componentNodes = 1;
        componentsStarted++;
        continue;
      }
      if (!batch.starts.isEmpty()) {
        N start = batch.starts.removeFirst();
        batch.startSet.remove(start);
        examined++;
        if (batch.covered.contains(start)) {
          skippedStarts++;
          continue;
        }
        if (topology.isNode(start)) {
          batch.seeds.addLast(start);
        } else {
          topology.forEachConnectedNode(
              start,
              connected -> {
                if (!batch.covered.contains(connected)) {
                  batch.seeds.addLast(connected);
                }
              });
        }
        continue;
      }
      batches.remove(batchEntry.getKey());
    }
    return new DrainResult(
        examined,
        refreshes,
        componentsStarted,
        componentsCompleted,
        skippedStarts,
        overflowedBatches,
        pendingStarts(),
        pendingWork());
  }

  boolean hasSealedWork() {
    return firstSealedBatch() != null;
  }

  int pendingWork() {
    int total = 0;
    for (Batch<N> batch : batches.values()) {
      total += batch.starts.size() + batch.seeds.size() + batch.frontier.size();
    }
    return total;
  }

  int pendingStarts() {
    int total = 0;
    for (Batch<N> batch : batches.values()) {
      total += batch.starts.size() + batch.seeds.size();
    }
    return total;
  }

  void clear() {
    batches.clear();
  }

  private Map.Entry<Long, Batch<N>> firstSealedBatch() {
    for (Map.Entry<Long, Batch<N>> entry : batches.entrySet()) {
      if (entry.getValue().sealed) {
        return entry;
      }
    }
    return null;
  }

  private static final class Batch<N> {
    private final ArrayDeque<N> starts = new ArrayDeque<>();
    private final Set<N> startSet = new HashSet<>();
    private final ArrayDeque<N> seeds = new ArrayDeque<>();
    private final ArrayDeque<N> frontier = new ArrayDeque<>();
    private final Set<N> covered = new HashSet<>();
    private boolean sealed;
    private boolean overflowed;
    private int componentNodes;

    private boolean addStart(N start) {
      if (!startSet.add(start)) {
        return false;
      }
      starts.addLast(start);
      return true;
    }

    private void clearPending() {
      starts.clear();
      startSet.clear();
      seeds.clear();
      frontier.clear();
    }
  }
}
