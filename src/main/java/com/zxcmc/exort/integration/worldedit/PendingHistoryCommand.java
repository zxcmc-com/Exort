package com.zxcmc.exort.integration.worldedit;

record PendingHistoryCommand(HistoryAction action, long timestampMs, int usesRemaining) {
  PendingHistoryCommand consume() {
    return new PendingHistoryCommand(action, timestampMs, usesRemaining - 1);
  }
}
