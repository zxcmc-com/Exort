package com.zxcmc.exort.display;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class BoundedRefreshQueue<T> {
  private final Set<T> queued = new LinkedHashSet<>();

  synchronized void enqueue(T value) {
    if (value != null) {
      queued.add(value);
    }
  }

  synchronized void enqueueAll(Collection<T> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    for (T value : values) {
      enqueue(value);
    }
  }

  synchronized void remove(T value) {
    queued.remove(value);
  }

  synchronized void clear() {
    queued.clear();
  }

  synchronized List<T> poll(int limit) {
    int count = Math.max(0, limit);
    List<T> result = new ArrayList<>(Math.min(count, queued.size()));
    var iterator = queued.iterator();
    while (iterator.hasNext() && result.size() < count) {
      T value = iterator.next();
      iterator.remove();
      result.add(value);
    }
    return result;
  }

  synchronized int size() {
    return queued.size();
  }
}
