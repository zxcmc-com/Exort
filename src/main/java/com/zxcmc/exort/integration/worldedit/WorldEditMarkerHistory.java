package com.zxcmc.exort.integration.worldedit;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

final class WorldEditMarkerHistory {
  private static final long HISTORY_TTL_MS = TimeUnit.MINUTES.toMillis(10);
  private static final int HISTORY_STACK_LIMIT = 16;

  private final Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> undoMarkerHistory =
      new ConcurrentHashMap<>();
  private final Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> redoMarkerHistory =
      new ConcurrentHashMap<>();

  void remember(
      UUID actorId,
      HistoryAction activeAction,
      UUID worldId,
      int x,
      int y,
      int z,
      MarkerSnapshot snapshot) {
    if (actorId == null || snapshot == null) return;
    long now = System.currentTimeMillis();
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> targetHistory;
    if (activeAction == HistoryAction.UNDO) {
      targetHistory = redoMarkerHistory;
    } else {
      targetHistory = undoMarkerHistory;
      if (activeAction == null) {
        redoMarkerHistory.remove(key);
      }
    }
    ConcurrentLinkedDeque<HistoryEntry> stack =
        targetHistory.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
    stack.addFirst(new HistoryEntry(snapshot, now));
    pruneHistoryStack(targetHistory, key, stack, now);
  }

  MarkerSnapshot peek(UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    if (actorId == null || action == null) return null;
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    HistoryEntry entry = peekValidHistory(historyFor(action), key);
    if (entry == null) return null;
    return entry.snapshot();
  }

  MarkerSnapshot consume(UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    if (actorId == null || action == null) return null;
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    HistoryEntry entry = pollValidHistory(historyFor(action), key);
    if (entry == null) return null;
    return entry.snapshot();
  }

  void clear() {
    undoMarkerHistory.clear();
    redoMarkerHistory.clear();
  }

  private Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> historyFor(HistoryAction action) {
    return action == HistoryAction.REDO ? redoMarkerHistory : undoMarkerHistory;
  }

  private HistoryEntry peekValidHistory(
      Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> history, HistoryKey key) {
    Deque<HistoryEntry> stack = history.get(key);
    if (stack == null) return null;
    long now = System.currentTimeMillis();
    while (true) {
      HistoryEntry entry = stack.peekFirst();
      if (entry == null) {
        history.remove(key, stack);
        return null;
      }
      if (now - entry.timestampMs() <= HISTORY_TTL_MS) {
        return entry;
      }
      stack.pollFirst();
    }
  }

  private HistoryEntry pollValidHistory(
      Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> history, HistoryKey key) {
    Deque<HistoryEntry> stack = history.get(key);
    if (stack == null) return null;
    long now = System.currentTimeMillis();
    while (true) {
      HistoryEntry entry = stack.pollFirst();
      if (entry == null) {
        history.remove(key, stack);
        return null;
      }
      if (stack.isEmpty()) {
        history.remove(key, stack);
      }
      if (now - entry.timestampMs() <= HISTORY_TTL_MS) {
        return entry;
      }
    }
  }

  private void pruneHistoryStack(
      Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> history,
      HistoryKey key,
      Deque<HistoryEntry> stack,
      long now) {
    while (true) {
      HistoryEntry entry = stack.peekLast();
      if (entry == null) {
        history.remove(key, stack);
        return;
      }
      if (stack.size() <= HISTORY_STACK_LIMIT && now - entry.timestampMs() <= HISTORY_TTL_MS) {
        return;
      }
      stack.pollLast();
    }
  }
}
