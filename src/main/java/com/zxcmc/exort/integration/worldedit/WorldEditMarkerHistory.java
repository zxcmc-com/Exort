package com.zxcmc.exort.integration.worldedit;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class WorldEditMarkerHistory {
  private static final long HISTORY_TTL_MS = TimeUnit.MINUTES.toMillis(10);
  private static final long MAINTENANCE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);
  private static final Limits DEFAULT_LIMITS =
      new Limits(100_000, 64L * 1024L * 1024L, 500_000, 256L * 1024L * 1024L, 4_096, 64, 128);

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
  private final Limits limits;
  private final RetentionBudget budget;

  WorldEditMarkerHistory() {
    this(DEFAULT_LIMITS);
  }

  WorldEditMarkerHistory(Limits limits) {
    this.limits = limits;
    this.budget = new RetentionBudget(limits);
  }

  synchronized Frame beginNormalOperation(UUID actorId, UUID worldId, long operationId) {
    if (actorId == null || worldId == null) return null;
    long now = System.currentTimeMillis();
    maintainIfDue(now);
    ActorWorldKey actorWorldKey = new ActorWorldKey(actorId, worldId);
    pruneFrameStack(undoFrames, actorWorldKey, now);
    clearRedo(actorWorldKey);
    FrameKey frameKey = new FrameKey(actorId, worldId, operationId);
    Frame existing = normalFrames.get(frameKey);
    if (existing != null) {
      return existing;
    }
    Frame created = Frame.create(actorId, worldId, operationId, now, limits, budget);
    if (created.overflowed()) {
      return created;
    }
    existing = normalFrames.putIfAbsent(frameKey, created);
    if (existing != null) {
      created.release();
      return existing;
    }
    ConcurrentLinkedDeque<Frame> stack =
        undoFrames.computeIfAbsent(actorWorldKey, key -> new ConcurrentLinkedDeque<>());
    stack.addFirst(created);
    trimFrameStack(undoFrames, actorWorldKey, stack);
    return created;
  }

  synchronized Frame beginReplay(UUID actorId, UUID worldId, HistoryAction action) {
    if (actorId == null || worldId == null || action == null) return null;
    long now = System.currentTimeMillis();
    maintainIfDue(now);
    ActorWorldKey actorWorldKey = new ActorWorldKey(actorId, worldId);
    Frame frame = pollValidFrame(framesFor(action), actorWorldKey, now);
    if (frame == null) return null;
    frame.refresh(now);
    Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> oppositeFrames = framesFor(opposite(action));
    ConcurrentLinkedDeque<Frame> oppositeStack =
        oppositeFrames.computeIfAbsent(actorWorldKey, ignored -> new ConcurrentLinkedDeque<>());
    oppositeStack.addFirst(frame);
    trimFrameStack(oppositeFrames, actorWorldKey, oppositeStack);
    return frame;
  }

  synchronized void remember(
      UUID actorId,
      HistoryAction activeAction,
      UUID worldId,
      int x,
      int y,
      int z,
      MarkerSnapshot snapshot) {
    remember(actorId, activeAction, worldId, x, y, z, snapshot, null);
  }

  synchronized void remember(
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
        releaseHistoryStack(redoMarkerHistory.remove(key));
        if (normalFrame != null) {
          normalFrame.rememberUndo(WorldEditMarkerMath.blockKey(x, y, z), snapshot);
          if (normalFrame.overflowed()) {
            return;
          }
        }
      }
    }
    retainHistory(targetHistory, key, FrameState.marker(snapshot), now, normalFrame);
  }

  synchronized void rememberRedoTarget(
      UUID actorId, UUID worldId, int x, int y, int z, MarkerSnapshot snapshot) {
    rememberRedoTarget(actorId, worldId, x, y, z, snapshot, null, false);
  }

  synchronized void rememberRedoTarget(
      UUID actorId, UUID worldId, int x, int y, int z, MarkerSnapshot snapshot, Frame normalFrame) {
    rememberRedoTarget(actorId, worldId, x, y, z, snapshot, normalFrame, false);
  }

  synchronized void rememberRedoTarget(
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
    if (normalFrame != null) {
      normalFrame.rememberRedo(
          WorldEditMarkerMath.blockKey(x, y, z), snapshot, storageCloneRequired);
      if (normalFrame.overflowed()) {
        return;
      }
    }
    retainHistory(
        redoMarkerHistory,
        key,
        FrameState.marker(snapshot, storageCloneRequired),
        now,
        normalFrame);
  }

  synchronized void clearRedoTarget(UUID actorId, UUID worldId, int x, int y, int z) {
    if (actorId == null) return;
    releaseHistoryStack(redoMarkerHistory.remove(new HistoryKey(actorId, worldId, x, y, z)));
  }

  synchronized MarkerSnapshot peek(
      UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
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

  synchronized FrameState peekState(
      UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    if (actorId == null || action == null) return null;
    maintainIfDue(System.currentTimeMillis());
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    HistoryEntry entry = peekValidHistory(historyFor(action), key);
    return entry == null ? null : entry.state();
  }

  synchronized MarkerSnapshot consume(
      UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    FrameState state = consumeState(actorId, action, worldId, x, y, z);
    return state == null ? null : state.snapshot();
  }

  synchronized FrameState consumeState(
      UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z) {
    if (actorId == null || action == null) return null;
    maintainIfDue(System.currentTimeMillis());
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    HistoryEntry entry = pollValidHistory(historyFor(action), key);
    return entry == null ? null : entry.state();
  }

  synchronized void rememberUndoClear(Frame normalFrame, int x, int y, int z) {
    rememberUndoClear(null, null, normalFrame, x, y, z);
  }

  synchronized void rememberUndoClear(
      UUID actorId, UUID worldId, Frame normalFrame, int x, int y, int z) {
    if (normalFrame == null) return;
    normalFrame.rememberUndoClear(WorldEditMarkerMath.blockKey(x, y, z));
    if (!normalFrame.overflowed()) {
      rememberClear(actorId, HistoryAction.UNDO, worldId, x, y, z, normalFrame);
    }
  }

  synchronized void rememberRedoClear(Frame normalFrame, int x, int y, int z) {
    rememberRedoClear(null, null, normalFrame, x, y, z);
  }

  synchronized void rememberRedoClear(
      UUID actorId, UUID worldId, Frame normalFrame, int x, int y, int z) {
    if (normalFrame == null) return;
    normalFrame.rememberRedoClear(WorldEditMarkerMath.blockKey(x, y, z));
    if (!normalFrame.overflowed()) {
      rememberClear(actorId, HistoryAction.REDO, worldId, x, y, z, normalFrame);
    }
  }

  synchronized void clear() {
    releaseAllHistory(undoMarkerHistory);
    releaseAllHistory(redoMarkerHistory);
    releaseAllFrames(undoFrames);
    releaseAllFrames(redoFrames);
    undoMarkerHistory.clear();
    redoMarkerHistory.clear();
    undoFrames.clear();
    redoFrames.clear();
    normalFrames.clear();
    nextMaintenanceAt.set(0L);
  }

  synchronized void clearActor(UUID actorId) {
    if (actorId == null) {
      return;
    }
    releaseActorHistory(undoMarkerHistory, actorId);
    releaseActorHistory(redoMarkerHistory, actorId);
    releaseActorFrames(undoFrames, actorId);
    releaseActorFrames(redoFrames, actorId);
    normalFrames.keySet().removeIf(key -> actorId.equals(key.actorId()));
  }

  private Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> historyFor(HistoryAction action) {
    return action == HistoryAction.REDO ? redoMarkerHistory : undoMarkerHistory;
  }

  private void rememberClear(
      UUID actorId, HistoryAction action, UUID worldId, int x, int y, int z, Frame normalFrame) {
    if (actorId == null || action == null) return;
    long now = System.currentTimeMillis();
    maintainIfDue(now);
    HistoryKey key = new HistoryKey(actorId, worldId, x, y, z);
    Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> targetHistory = historyFor(action);
    retainHistory(targetHistory, key, FrameState.cleared(), now, normalFrame);
  }

  private Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> framesFor(HistoryAction action) {
    return action == HistoryAction.REDO ? redoFrames : undoFrames;
  }

  private static HistoryAction opposite(HistoryAction action) {
    return action == HistoryAction.REDO ? HistoryAction.UNDO : HistoryAction.REDO;
  }

  private void clearRedo(ActorWorldKey actorWorldKey) {
    releaseFrameStack(redoFrames.remove(actorWorldKey));
    redoMarkerHistory.forEach(
        (key, stack) -> {
          if (actorWorldKey.actorId().equals(key.actorId())
              && actorWorldKey.worldId().equals(key.worldId())
              && redoMarkerHistory.remove(key, stack)) {
            releaseHistoryStack(stack);
          }
        });
  }

  private void retainHistory(
      Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> history,
      HistoryKey key,
      FrameState state,
      long now,
      Frame normalFrame) {
    long weight = state.estimatedBytes();
    if (!budget.reserveState(weight)) {
      if (normalFrame != null) {
        normalFrame.markOverflow();
      }
      return;
    }
    ConcurrentLinkedDeque<HistoryEntry> stack =
        history.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
    stack.addFirst(new HistoryEntry(state, now));
    while (stack.size() > limits.maxEntriesPerHistoryKey()) {
      HistoryEntry evicted = stack.pollLast();
      if (evicted == null) {
        break;
      }
      budget.releaseState(evicted.state().estimatedBytes());
    }
    pruneHistoryStack(history, key, stack, now);
  }

  private void trimFrameStack(
      Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> frames,
      ActorWorldKey key,
      ConcurrentLinkedDeque<Frame> stack) {
    while (stack.size() > limits.maxFramesPerActorWorld()) {
      Frame evicted = stack.pollLast();
      if (evicted == null) {
        break;
      }
      normalFrames.remove(
          new FrameKey(evicted.actorId(), evicted.worldId(), evicted.operationId()), evicted);
      evicted.release();
    }
    if (stack.isEmpty()) {
      frames.remove(key, stack);
    }
  }

  private void releaseHistoryStack(Deque<HistoryEntry> stack) {
    if (stack == null) return;
    HistoryEntry entry;
    while ((entry = stack.pollFirst()) != null) {
      budget.releaseState(entry.state().estimatedBytes());
    }
  }

  private void releaseFrameStack(Deque<Frame> stack) {
    if (stack == null) return;
    Frame frame;
    while ((frame = stack.pollFirst()) != null) {
      normalFrames.remove(
          new FrameKey(frame.actorId(), frame.worldId(), frame.operationId()), frame);
      frame.release();
    }
  }

  private void releaseAllHistory(Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> history) {
    history.values().forEach(this::releaseHistoryStack);
  }

  private void releaseAllFrames(Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> frames) {
    frames.values().forEach(this::releaseFrameStack);
  }

  private void releaseActorHistory(
      Map<HistoryKey, ConcurrentLinkedDeque<HistoryEntry>> history, UUID actorId) {
    history.forEach(
        (key, stack) -> {
          if (actorId.equals(key.actorId()) && history.remove(key, stack)) {
            releaseHistoryStack(stack);
          }
        });
  }

  private void releaseActorFrames(
      Map<ActorWorldKey, ConcurrentLinkedDeque<Frame>> frames, UUID actorId) {
    frames.forEach(
        (key, stack) -> {
          if (actorId.equals(key.actorId()) && frames.remove(key, stack)) {
            releaseFrameStack(stack);
          }
        });
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
      if (stack.pollFirst() == entry) {
        budget.releaseState(entry.state().estimatedBytes());
      }
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
      budget.releaseState(entry.state().estimatedBytes());
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
      frame.release();
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
      frame.release();
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
      if (stack.pollLast() == entry) {
        budget.releaseState(entry.state().estimatedBytes());
      }
    }
  }

  synchronized void pruneExpired(long now) {
    pruneExpiredHistory(undoMarkerHistory, now);
    pruneExpiredHistory(redoMarkerHistory, now);
    pruneExpiredFrames(undoFrames, now);
    pruneExpiredFrames(redoFrames, now);
    normalFrames.forEach(
        (key, frame) -> {
          if (frame.expired(now)) {
            if (normalFrames.remove(key, frame)) {
              frame.release();
            }
          }
        });
  }

  synchronized int retainedKeyCount() {
    return undoMarkerHistory.size()
        + redoMarkerHistory.size()
        + undoFrames.size()
        + redoFrames.size()
        + normalFrames.size();
  }

  synchronized long retainedStateCount() {
    return budget.retainedStates();
  }

  synchronized long retainedWeightBytes() {
    return budget.retainedBytes();
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
    private final Limits limits;
    private final RetentionBudget budget;
    private final ConcurrentMap<Long, FrameState> undoStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, FrameState> redoStates = new ConcurrentHashMap<>();
    private final AtomicBoolean released = new AtomicBoolean();
    private volatile long timestampMs;
    private long retainedStates;
    private long retainedBytes;
    private volatile boolean overflowed;

    private Frame(
        UUID actorId,
        UUID worldId,
        long operationId,
        long timestampMs,
        Limits limits,
        RetentionBudget budget,
        boolean overflowed) {
      this.actorId = actorId;
      this.worldId = worldId;
      this.operationId = operationId;
      this.timestampMs = timestampMs;
      this.limits = limits;
      this.budget = budget;
      this.overflowed = overflowed;
    }

    static Frame create(
        UUID actorId,
        UUID worldId,
        long operationId,
        long timestampMs,
        Limits limits,
        RetentionBudget budget) {
      boolean accepted = budget.reserveFrame();
      return new Frame(actorId, worldId, operationId, timestampMs, limits, budget, !accepted);
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

    synchronized void rememberUndo(long blockKey, MarkerSnapshot snapshot) {
      if (snapshot != null) {
        rememberIfAbsent(undoStates, blockKey, FrameState.marker(snapshot));
      }
    }

    void rememberRedo(long blockKey, MarkerSnapshot snapshot) {
      rememberRedo(blockKey, snapshot, false);
    }

    synchronized void rememberRedo(
        long blockKey, MarkerSnapshot snapshot, boolean storageCloneRequired) {
      if (snapshot != null) {
        rememberReplacing(redoStates, blockKey, FrameState.marker(snapshot, storageCloneRequired));
      }
    }

    synchronized void rememberUndoClear(long blockKey) {
      rememberIfAbsent(undoStates, blockKey, FrameState.cleared());
    }

    synchronized void rememberRedoClear(long blockKey) {
      rememberReplacing(redoStates, blockKey, FrameState.cleared());
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

    boolean overflowed() {
      return overflowed;
    }

    void markOverflow() {
      overflowed = true;
    }

    synchronized void release() {
      if (!released.compareAndSet(false, true)) {
        return;
      }
      overflowed = true;
      undoStates.clear();
      redoStates.clear();
      budget.releaseFrame(retainedStates, retainedBytes);
      retainedStates = 0L;
      retainedBytes = 0L;
    }

    private void rememberIfAbsent(
        ConcurrentMap<Long, FrameState> states, long blockKey, FrameState state) {
      if (overflowed || released.get() || states.containsKey(blockKey)) {
        return;
      }
      long weight = state.estimatedBytes();
      if (!canRetain(1L, weight) || !budget.reserveState(weight)) {
        overflowed = true;
        return;
      }
      states.put(blockKey, state);
      retainedStates++;
      retainedBytes += weight;
    }

    private void rememberReplacing(
        ConcurrentMap<Long, FrameState> states, long blockKey, FrameState state) {
      if (overflowed || released.get()) {
        return;
      }
      FrameState previous = states.get(blockKey);
      long previousWeight = previous == null ? 0L : previous.estimatedBytes();
      long weight = state.estimatedBytes();
      long stateDelta = previous == null ? 1L : 0L;
      long weightDelta = weight - previousWeight;
      if (!canRetain(stateDelta, weightDelta)
          || !budget.replaceState(previous == null, previousWeight, weight)) {
        overflowed = true;
        return;
      }
      states.put(blockKey, state);
      retainedStates += stateDelta;
      retainedBytes += weightDelta;
    }

    private boolean canRetain(long stateDelta, long weightDelta) {
      return retainedStates + stateDelta <= limits.maxFrameStates()
          && retainedBytes + weightDelta <= limits.maxFrameWeightBytes();
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

    long estimatedBytes() {
      if (snapshot == null) {
        return 64L;
      }
      long bytes = 512L;
      if (snapshot.bus() != null) {
        bytes += snapshot.bus().estimatedPayloadBytes();
      }
      if (snapshot.monitor() != null) {
        bytes += snapshot.monitor().estimatedPayloadBytes();
      }
      if (snapshot.transmitterData() != null) {
        bytes += snapshot.transmitterData().estimatedPayloadBytes();
      }
      return bytes;
    }
  }

  record Limits(
      long maxFrameStates,
      long maxFrameWeightBytes,
      long maxRetainedStates,
      long maxRetainedWeightBytes,
      long maxFrames,
      int maxFramesPerActorWorld,
      int maxEntriesPerHistoryKey) {
    Limits {
      if (maxFrameStates <= 0
          || maxFrameWeightBytes <= 0
          || maxRetainedStates <= 0
          || maxRetainedWeightBytes <= 0
          || maxFrames <= 0
          || maxFramesPerActorWorld <= 0
          || maxEntriesPerHistoryKey <= 0) {
        throw new IllegalArgumentException("WorldEdit history limits must be positive");
      }
    }
  }

  private static final class RetentionBudget {
    private final Limits limits;
    private long frames;
    private long retainedStates;
    private long retainedBytes;

    private RetentionBudget(Limits limits) {
      this.limits = limits;
    }

    synchronized boolean reserveFrame() {
      if (frames >= limits.maxFrames()) {
        return false;
      }
      frames++;
      return true;
    }

    synchronized boolean reserveState(long weight) {
      if (retainedStates >= limits.maxRetainedStates()
          || retainedBytes + weight > limits.maxRetainedWeightBytes()) {
        return false;
      }
      retainedStates++;
      retainedBytes += weight;
      return true;
    }

    synchronized boolean replaceState(
        boolean addingState, long previousWeight, long replacementWeight) {
      long statesAfter = retainedStates + (addingState ? 1L : 0L);
      long bytesAfter = retainedBytes - previousWeight + replacementWeight;
      if (statesAfter > limits.maxRetainedStates()
          || bytesAfter > limits.maxRetainedWeightBytes()) {
        return false;
      }
      retainedStates = statesAfter;
      retainedBytes = bytesAfter;
      return true;
    }

    synchronized void releaseState(long weight) {
      retainedStates = Math.max(0L, retainedStates - 1L);
      retainedBytes = Math.max(0L, retainedBytes - weight);
    }

    synchronized void releaseFrame(long states, long bytes) {
      frames = Math.max(0L, frames - 1L);
      retainedStates = Math.max(0L, retainedStates - states);
      retainedBytes = Math.max(0L, retainedBytes - bytes);
    }

    synchronized long retainedStates() {
      return retainedStates;
    }

    synchronized long retainedBytes() {
      return retainedBytes;
    }
  }

  private record ActorWorldKey(UUID actorId, UUID worldId) {}

  private record FrameKey(UUID actorId, UUID worldId, long operationId) {}
}
