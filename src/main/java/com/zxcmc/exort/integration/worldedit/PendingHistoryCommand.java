package com.zxcmc.exort.integration.worldedit;

import java.util.concurrent.atomic.AtomicInteger;

record PendingHistoryCommand(
    HistoryAction action,
    int steps,
    long timestampMs,
    int usesRemaining,
    AtomicInteger replayFramesStarted) {
  PendingHistoryCommand(HistoryAction action, int steps, long timestampMs, int usesRemaining) {
    this(action, steps, timestampMs, usesRemaining, new AtomicInteger());
  }

  PendingHistoryCommand consume() {
    return new PendingHistoryCommand(
        action, steps, timestampMs, Math.max(1, usesRemaining - 1), replayFramesStarted);
  }
}
