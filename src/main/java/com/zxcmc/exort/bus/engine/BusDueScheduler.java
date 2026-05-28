package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.bus.BusState;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
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
    scheduledTicks.keySet().removeIf(state -> !live.contains(state));
    registryVersion = version;
  }

  void schedule(BusState state, long tick) {
    if (state == null) {
      return;
    }
    scheduledTicks.put(state, tick);
    dueBuckets.computeIfAbsent(tick, ignored -> new ArrayDeque<>()).addLast(state);
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
}
