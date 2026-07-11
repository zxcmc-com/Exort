package com.zxcmc.exort.feedback;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class FeedbackCooldown {
  private static final int MAX_ENTRIES = 8_192;
  private static final long STALE_AFTER_MILLIS = 10L * 60L * 1_000L;
  private final long cooldownMillis;
  private final Map<FeedbackKey, Long> lastFeedback = new LinkedHashMap<>();

  FeedbackCooldown(long cooldownMillis) {
    this.cooldownMillis = Math.max(0L, cooldownMillis);
  }

  boolean shouldSend(UUID playerId, String style, String message, long nowMillis) {
    if (playerId == null || style == null || message == null) return false;
    return shouldSend(playerId, style + '\u0000' + message, nowMillis, cooldownMillis);
  }

  synchronized boolean shouldSend(
      UUID playerId, String reason, long nowMillis, long reasonCooldownMillis) {
    if (playerId == null || reason == null) return false;
    long safeCooldownMillis = Math.max(0L, reasonCooldownMillis);
    FeedbackKey key = new FeedbackKey(playerId, reason);
    Long previous = lastFeedback.get(key);
    if (previous != null && nowMillis - previous < safeCooldownMillis) {
      return false;
    }
    lastFeedback.remove(key);
    lastFeedback.put(key, nowMillis);
    if (lastFeedback.size() > MAX_ENTRIES) {
      long cutoff = nowMillis - STALE_AFTER_MILLIS;
      lastFeedback.entrySet().removeIf(entry -> entry.getValue() < cutoff);
      Iterator<FeedbackKey> oldest = lastFeedback.keySet().iterator();
      while (lastFeedback.size() > MAX_ENTRIES && oldest.hasNext()) {
        oldest.next();
        oldest.remove();
      }
    }
    return true;
  }

  synchronized int size() {
    return lastFeedback.size();
  }

  private record FeedbackKey(UUID playerId, String reason) {}
}
