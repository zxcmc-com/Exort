package com.zxcmc.exort.display.device;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main-thread-confined cooldown state for monitor-triggered Storage loads. */
final class MonitorLoadAttemptTracker {
  private final int maxEntries;
  private final long cooldownNanos;
  private final LinkedHashMap<String, Long> attempts = new LinkedHashMap<>();

  MonitorLoadAttemptTracker(int maxEntries, long cooldownNanos) {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("maxEntries must be positive");
    }
    if (cooldownNanos < 0L) {
      throw new IllegalArgumentException("cooldownNanos must not be negative");
    }
    this.maxEntries = maxEntries;
    this.cooldownNanos = cooldownNanos;
  }

  boolean tryStart(String storageId, long nowNanos) {
    if (storageId == null || storageId.isBlank()) {
      return false;
    }
    Long previous = attempts.get(storageId);
    if (previous != null && nowNanos - previous < cooldownNanos) {
      return false;
    }
    attempts.remove(storageId);
    attempts.put(storageId, nowNanos);
    trimToLimit();
    return true;
  }

  void complete(String storageId) {
    attempts.remove(storageId);
  }

  void forget(String storageId) {
    attempts.remove(storageId);
  }

  void clear() {
    attempts.clear();
  }

  int size() {
    return attempts.size();
  }

  boolean contains(String storageId) {
    return attempts.containsKey(storageId);
  }

  private void trimToLimit() {
    Iterator<Map.Entry<String, Long>> iterator = attempts.entrySet().iterator();
    while (attempts.size() > maxEntries && iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }
  }
}
