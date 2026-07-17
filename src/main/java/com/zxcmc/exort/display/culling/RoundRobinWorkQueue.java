package com.zxcmc.exort.display.culling;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Main-thread-confined unique work queue that rotates fairly after every poll. */
final class RoundRobinWorkQueue<T> {
  private final ArrayDeque<T> queue = new ArrayDeque<>();
  private final Set<T> values = new HashSet<>();

  boolean add(T value) {
    if (value == null || !values.add(value)) {
      return false;
    }
    queue.addLast(value);
    return true;
  }

  boolean remove(T value) {
    if (!values.remove(value)) {
      return false;
    }
    queue.remove(value);
    return true;
  }

  T next() {
    T value = queue.pollFirst();
    if (value != null) {
      queue.addLast(value);
    }
    return value;
  }

  void clear() {
    queue.clear();
    values.clear();
  }

  int size() {
    return queue.size();
  }
}
