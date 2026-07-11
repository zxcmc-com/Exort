package com.zxcmc.exort.integration.worldedit;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class WorldEditMarkerHistory {
  private static final long HISTORY_TTL_MS = TimeUnit.MINUTES.toMillis(10);
  private static final long MAINTENANCE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);

  private final Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> undoMarkerHistory =
      new ConcurrentHashMap<>();
  private final Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> redoMarkerHistory =
      new ConcurrentHashMap<>();
  private final Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> undoFrames =
      new ConcurrentHashMap<>();
  private final Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> redoFrames =
      new ConcurrentHashMap<>();
  private final Map<FrameKey, Frame> normalFrames = new ConcurrentHashMap<>();
  private final AtomicLong nextMaintenanceAt = new AtomicLong();

  Frame beginNormalOperation(UUID actorId, UUID worldId, long operationId) {
    if (actorId == null || worldId == null) return null;
    long now = System.currentTimeMillis();
    maintainIfDue(now);
    ActorWorldKey actorWorldKey = new ActorWorldKey(actorId, worldId);
    pruneFrameStack(undoFrames, actorWorldKey, now);
    clearRedo(actorWorldKey);
    FrameKey frameKey = new FrameKey(actorId, worldId, operationId);
    return normalFrames.computeIfAbsent(
        frameKey,
        ignored -> {
          Frame frame = new Frame(actorId, worldId, operationId, now);
          undoFrames
              .computeIfAbsent(actorWorldKey, key -> new ConcurrentLinkedDeque<>())
              .addFirst(frame);
          return frame;
        });
  }

  Frame beginReplay(UUID actorId, UUID worldId, HistoryAction action) {
    if (actorId == null || worldId == null || action == null) return null;
    long now = System.currentTimeMillis();
    maintainIfDue(now);
    ActorWorldKey actorWorldKey = new ActorWorldKey(actorId, worldId);
    Frame frame = pollValidFrame(framesFor(action), actorWorldKey, now);
    if (frame == null) return null;
    frame.refresh(now);
    framesFor(opposite(action))
        .computeIfAbsent(actorWorldKey, ignored -> new ConcurrentLinkedDeque<>())
        .addFirst(frame);
    return frame;
  }

  void remember(
      UUID actorId,
      HistoryAction activeAction,
      UUID worldId,
      int x,
      int y,
      int z,
      MarkerSnapshot snapshot) {
    remember(actorId, activeAction, worldId, x, y, z, snapshot, null);
  }

  void remember(
      UUID actorId,
      HistoryAction activeAction,
      UUID worldId,
      int x,
      int y,
      int z,
      MarkerSnapshot snapshot,
      Frame normalFrame) {
    if (actorId == null || snapshot == null) return;
    long now = System.currentTimeMillis();
    maintainIfDue(now);
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> targetHistory;
    if (activeAction == HistoryAction.UNDO) {
      targetHistory = redoMarkerHistory;
    } else {
      targetHistory = undoMarkerHistory;
      if (activeAction == null) {
        redoMarkerHistory.remove(key);
        if (normalFrame != null) {
          normalFrame.rememberUndo(WorldEditMarkerMath.blockKey(x, y, z), snapshot);
        }
      }
    }
    ConcurrentLinkedDeque<HistoryEntry> stack =
        targetHistory.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
    stack.addFirst(new HistoryEntry(FrameState.marker(snapshot), now));
    pruneHistoryStack(targetHistory, key, stack, now);
  }

  void rememberRedoTarget(
      UUID actorId, UUID worldId, int x, int y, int z, MarkerSnapshot snapshot) {
    rememberRedoTarget(actorId, worldId, x, y, z, snapshot, null, false);
  }

  void rememberRedoTarget(
      UUID actorId, UUID worldId, int x, int y, int z, MarkerSnapshot snapshot, Frame normalFrame) {
    rememberRedoTarget(actorId, worldId, x, y, z, snapshot, normalFrame, false);
  }

  void rememberRedoTarget(
      UUID actorId,
      UUID worldId,
      int x,
      int y,
      int z,
      MarkerSnapshot snapshot,
      Frame normalFrame,
      boolean storageCloneRequired) {
    if (actorId == null || snapshot == null) return;
    long now = System.currentTimeMillis();
    maintainIfDue(now);
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    ConcurrentLinkedDeque<HistoryEntry> stack =
        redoMarkerHistory.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
    stack.addFirst(new HistoryEntry(FrameState.marker(snapshot, storageCloneRequired), now));
    if (normalFrame != null) {
      normalFrame.rememberRedo(
          WorldEditMarkerMath.blockKey(x, y, z), snapshot, storageCloneRequired);
    }
    pruneHistoryStack(redoMarkerHistory, key, stack, now);
  }

  void clearRedoTarget(UUID actorId, UUID worldId, int x, int y, int z) {
    if (actorId == null) return;
    redoMarkerHistory.remove(new HistoryKey(actorId, worldId, x, y, z));
  }

  MarkerSnapshot peek(UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    FrameState state = peekState(actorId, action, worldId, x, y, z);
    return state == null ? null : state.snapshot();
  }

  MarkerSnapshot peek(Frame frame, HistoryAction action, int x, int y, int z) {
    if (frame == null || action == null) return null;
    return frame.snapshot(action, WorldEditMarkerMath.blockKey(x, y, z));
  }

  FrameState peekState(Frame frame, HistoryAction action, int x, int y, int z) {
    if (frame == null || action == null) return null;
    return frame.state(action, WorldEditMarkerMath.blockKey(x, y, z));
  }

  FrameState peekState(UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    if (actorId == null || action == null) return null;
    maintainIfDue(System.currentTimeMillis());
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    HistoryEntry entry = peekValidHistory(historyFor(action), key);
    return entry == null ? null : entry.state();
  }

  MarkerSnapshot consume(UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    FrameState state = consumeState(actorId, action, worldId, x, y, z);
    return state == null ? null : state.snapshot();
  }

  FrameState consumeState(UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    if (actorId == null || action == null) return null;
    maintainIfDue(System.currentTimeMillis());
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    HistoryEntry entry = pollValidHistory(historyFor(action), key);
    return entry == null ? null : entry.state();
  }

  void rememberUndoClear(Frame normalFrame, int x, int y, int z) {
    rememberUndoClear(null, null, normalFrame, x, y, z);
  }

  void rememberUndoClear(UUID actorId, UUID worldId, Frame normalFrame, int x, int y, int z) {
    if (normalFrame == null) return;
    normalFrame.rememberUndoClear(WorldEditMarkerMath.blockKey(x, y, z));
    rememberClear(actorId, HistoryAction.UNDO, worldId, x, y, z);
  }

  void rememberRedoClear(Frame normalFrame, int x, int y, int z) {
    rememberRedoClear(null, null, normalFrame, x, y, z);
  }

  void rememberRedoClear(UUID actorId, UUID worldId, Frame normalFrame, int x, int y, int z) {
    if (normalFrame == null) return;
    normalFrame.rememberRedoClear(WorldEditMarkerMath.blockKey(x, y, z));
    rememberClear(actorId, HistoryAction.REDO, worldId, x, y, z);
  }

  void clear() {
    undoMarkerHistory.clear();
    redoMarkerHistory.clear();
    undoFrames.clear();
    redoFrames.clear();
    normalFrames.clear();
    nextMaintenanceAt.set(0L);
  }

  void clearActor(UUID actorId) {
    if (actorId == null) {
      return;
    }
    undoMarkerHistory.keySet().removeIf(key -> actorId.equals(key.actorId()));
    redoMarkerHistory.keySet().removeIf(key -> actorId.equals(key.actorId()));
    undoFrames.keySet().removeIf(key -> actorId.equals(key.actorId()));
    redoFrames.keySet().removeIf(key -> actorId.equals(key.actorId()));
    normalFrames.keySet().removeIf(key -> actorId.equals(key.actorId()));
  }

  private Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> historyFor(HistoryAction action) {
    return action == HistoryAction.REDO ? redoMarkerHistory : undoMarkerHistory;
  }

  private void rememberClear(
      UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    if (actorId == null || action == null) return;
    long now = System.currentTimeMillis();
    maintainIfDue(now);
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> targetHistory = historyFor(action);
    ConcurrentLinkedDeque<HistoryEntry> stack =
        targetHistory.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
    stack.addFirst(new HistoryEntry(FrameState.cleared(), now));
    pruneHistoryStack(targetHistory, key, stack, now);
  }

  private Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> framesFor(HistoryAction action) {
    return action == HistoryAction.REDO ? redoFrames : undoFrames;
  }

  private static HistoryAction opposite(HistoryAction action) {
    return action == HistoryAction.REDO ? HistoryAction.UNDO : HistoryAction.REDO;
  }

  private void clearRedo(ActorWorldKey actorWorldKey) {
    redoFrames.remove(actorWorldKey);
    redoMarkerHistory
        .keySet()
        .removeIf(
            key ->
                actorWorldKey.actorId().equals(key.actorId())
                    && actorWorldKey.worldId().equals(key.worldId()));
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

  private Frame pollValidFrame(
      Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> frames, ActorWorldKey key, long now) {
    Deque<Frame> stack = frames.get(key);
    if (stack == null) return null;
    while (true) {
      Frame frame = stack.pollFirst();
      if (frame == null) {
        frames.remove(key, stack);
        return null;
      }
      normalFrames.remove(
          new FrameKey(frame.actorId(), frame.worldId(), frame.operationId()), frame);
      if (stack.isEmpty()) {
        frames.remove(key, stack);
      }
      if (!frame.expired(now)) {
        return frame;
      }
    }
  }

  private void pruneFrameStack(
      Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> frames, ActorWorldKey key, long now) {
    Deque<Frame> stack = frames.get(key);
    if (stack == null) return;
    while (true) {
      Frame frame = stack.peekLast();
      if (frame == null) {
        frames.remove(key, stack);
        return;
      }
      if (!frame.expired(now)) {
        return;
      }
      stack.pollLast();
      normalFrames.remove(
          new FrameKey(frame.actorId(), frame.worldId(), frame.operationId()), frame);
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
      if (now - entry.timestampMs() <= HISTORY_TTL_MS) {
        return;
      }
      stack.pollLast();
    }
  }

  void pruneExpired(long now) {
    pruneExpiredHistory(undoMarkerHistory, now);
    pruneExpiredHistory(redoMarkerHistory, now);
    pruneExpiredFrames(undoFrames, now);
    pruneExpiredFrames(redoFrames, now);
    normalFrames.forEach(
        (key, frame) -> {
          if (frame.expired(now)) {
            normalFrames.remove(key, frame);
          }
        });
  }

  int retainedKeyCount() {
    return undoMarkerHistory.size()
        + redoMarkerHistory.size()
        + undoFrames.size()
        + redoFrames.size()
        + normalFrames.size();
  }

  private void maintainIfDue(long now) {
    long scheduled = nextMaintenanceAt.get();
    if (now < scheduled
        || !nextMaintenanceAt.compareAndSet(scheduled, now + MAINTENANCE_INTERVAL_MS)) {
      return;
    }
    pruneExpired(now);
  }

  private void pruneExpiredHistory(
      Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> history, long now) {
    history.forEach((key, stack) -> pruneHistoryStack(history, key, stack, now));
  }

  private void pruneExpiredFrames(
      Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> frames, long now) {
    frames.forEach((key, stack) -> pruneFrameStack(frames, key, now));
  }

  static final class Frame {
    private final UUID actorId;
    private final UUID worldId;
    private final long operationId;
    private final ConcurrentMap<Long, FrameState> undoStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, FrameState> redoStates = new ConcurrentHashMap<>();
    private volatile long timestampMs;

    private Frame(UUID actorId, UUID worldId, long operationId, long timestampMs) {
      this.actorId = actorId;
      this.worldId = worldId;
      this.operationId = operationId;
      this.timestampMs = timestampMs;
    }

    UUID actorId() {
      return actorId;
    }

    UUID worldId() {
      return worldId;
    }

    long operationId() {
      return operationId;
    }

    void rememberUndo(long blockKey, MarkerSnapshot snapshot) {
      if (snapshot != null) {
        undoStates.putIfAbsent(blockKey, FrameState.marker(snapshot));
      }
    }

    void rememberRedo(long blockKey, MarkerSnapshot snapshot) {
      rememberRedo(blockKey, snapshot, false);
    }

    void rememberRedo(long blockKey, MarkerSnapshot snapshot, boolean storageCloneRequired) {
      if (snapshot != null) {
        redoStates.put(blockKey, FrameState.marker(snapshot, storageCloneRequired));
      }
    }

    void rememberUndoClear(long blockKey) {
      undoStates.putIfAbsent(blockKey, FrameState.cleared());
    }

    void rememberRedoClear(long blockKey) {
      redoStates.put(blockKey, FrameState.cleared());
    }

    FrameState state(HistoryAction action, long blockKey) {
      return action == HistoryAction.REDO ? redoStates.get(blockKey) : undoStates.get(blockKey);
    }

    MarkerSnapshot snapshot(HistoryAction action, long blockKey) {
      FrameState state = state(action, blockKey);
      return state == null ? null : state.snapshot();
    }

    void refresh(long timestampMs) {
      this.timestampMs = timestampMs;
    }

    boolean expired(long now) {
      return now - timestampMs > HISTORY_TTL_MS;
    }
  }

  record FrameState(MarkerSnapshot snapshot, boolean clear, boolean storageCloneRequired) {
    static FrameState marker(MarkerSnapshot snapshot) {
      return marker(snapshot, false);
    }

    static FrameState marker(MarkerSnapshot snapshot, boolean storageCloneRequired) {
      return new FrameState(snapshot, false, storageCloneRequired);
    }

    static FrameState cleared() {
      return new FrameState(null, true, false);
    }
  }

  private record ActorWorldKey(UUID actorId, UUID worldId) {}

  private record FrameKey(UUID actorId, UUID worldId, long operationId) {}
}
