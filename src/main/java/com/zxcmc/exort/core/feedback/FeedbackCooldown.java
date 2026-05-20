package com.zxcmc.exort.core.feedback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class FeedbackCooldown {
  private final long cooldownMillis;
  private final Map<UUID, LastFeedback> lastFeedback = new ConcurrentHashMap<>();

  FeedbackCooldown(long cooldownMillis) {
    this.cooldownMillis = Math.max(0L, cooldownMillis);
  }

  boolean shouldSend(UUID playerId, String style, String message, long nowMillis) {
    if (playerId == null || style == null || message == null) return false;
    LastFeedback previous = lastFeedback.get(playerId);
    if (previous != null
        && previous.style().equals(style)
        && previous.message().equals(message)
        && nowMillis - previous.sentAtMillis() < cooldownMillis) {
      return false;
    }
    lastFeedback.put(playerId, new LastFeedback(style, message, nowMillis));
    return true;
  }

  private record LastFeedback(String style, String message, long sentAtMillis) {}
}
