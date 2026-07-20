package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.bus.BusState;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class BusDueScheduler {
  private final TreeMap<Long, ArrayDeque<BusState>> dueBuckets = new TreeMap<>();
  private final IdentityHashMap<BusState, Long> scheduledTicks = new IdentityHashMap<>();
  private long registryVersion = Long.MIN_VALUE;

  void clear() {
    dueBuckets.clear();
    scheduledTicks.clear();
    registryVersion = Long.MIN_VALUE;
  }

  int size() {
    return scheduledTicks.size();
  }

  boolean hasDue(long tick) {
    return !dueBuckets.isEmpty() && dueBuckets.firstKey() <= tick;
  }

  void sync(List<BusState> states, long version, long tick) {
    if (version == registryVersion) {
      return;
    }
    Set<BusState> live = Collections.newSetFromMap(new IdentityHashMap<>());
    for (BusState state : states) {
      if (state == null) continue;
      live.add(state);
      if (!scheduledTicks.containsKey(state)) {
        schedule(state, Math.max(tick, state.nextTick()));
      }
    }
    Iterator<Map.Entry<BusState, Long>> scheduled = scheduledTicks.entrySet().iterator();
    while (scheduled.hasNext()) {
      Map.Entry<BusState, Long> entry = scheduled.next();
      if (live.contains(entry.getKey())) {
        continue;
      }
      BusState removedState = entry.getKey();
      long removedTick = entry.getValue();
      scheduled.remove();
      removeFromBucket(removedState, removedTick);
    }
    registryVersion = version;
  }

  void schedule(BusState state, long tick) {
    if (state == null) {
      return;
    }
    Long previousTick = scheduledTicks.get(state);
    if (previousTick != null) {
      if (previousTick == tick) {
        return;
      }
      removeFromBucket(state, previousTick);
    }
    scheduledTicks.put(state, tick);
    dueBuckets.computeIfAbsent(tick, ignored -> new ArrayDeque<>()).addLast(state);
  }

  int queuedEntryCount() {
    int total = 0;
    for (ArrayDeque<BusState> bucket : dueBuckets.values()) {
      total += bucket.size();
    }
    return total;
  }

  BusState pollDue(long tick) {
    while (!dueBuckets.isEmpty()) {
      var first = dueBuckets.firstEntry();
      if (first.getKey() > tick) {
        return null;
      }
      long scheduledTick = first.getKey();
      ArrayDeque<BusState> bucket = first.getValue();
      BusState state = bucket.pollFirst();
      if (bucket.isEmpty()) {
        dueBuckets.pollFirstEntry();
      }
      if (state == null) {
        continue;
      }
      Long expected = scheduledTicks.get(state);
      if (expected == null || expected.longValue() != scheduledTick) {
        continue;
      }
      scheduledTicks.remove(state);
      return state;
    }
    return null;
  }

  private void removeFromBucket(BusState state, long tick) {
    ArrayDeque<BusState> bucket = dueBuckets.get(tick);
    if (bucket == null) {
      return;
    }
    bucket.removeFirstOccurrence(state);
    if (bucket.isEmpty()) {
      dueBuckets.remove(tick);
    }
  }
}
