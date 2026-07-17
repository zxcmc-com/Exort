package com.zxcmc.exort.display.device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Main-thread-confined ordered set with bounded round-robin snapshots. */
final class RoundRobinIndex<T> {
  private final List<T> entries = new ArrayList<>();
  private final Map<T, Integer> positions = new HashMap<>();
  private int cursor;

  boolean add(T value) {
    if (value == null || positions.containsKey(value)) {
      return false;
    }
    positions.put(value, entries.size());
    entries.add(value);
    return true;
  }

  boolean remove(T value) {
    Integer removedIndex = positions.remove(value);
    if (removedIndex == null) {
      return false;
    }
    entries.remove((int) removedIndex);
    for (int index = removedIndex; index < entries.size(); index++) {
      positions.put(entries.get(index), index);
    }
    if (removedIndex < cursor) {
      cursor--;
    }
    normalizeCursor();
    return true;
  }

  List<T> nextBatch(int limit) {
    if (limit <= 0 || entries.isEmpty()) {
      return List.of();
    }
    int count = Math.min(limit, entries.size());
    List<T> batch = new ArrayList<>(count);
    normalizeCursor();
    for (int index = 0; index < count; index++) {
      batch.add(entries.get((cursor + index) % entries.size()));
    }
    cursor = (cursor + count) % entries.size();
    return batch;
  }

  void clear() {
    entries.clear();
    positions.clear();
    cursor = 0;
  }

  int size() {
    return entries.size();
  }

  private void normalizeCursor() {
    cursor = entries.isEmpty() ? 0 : Math.floorMod(cursor, entries.size());
  }
}
