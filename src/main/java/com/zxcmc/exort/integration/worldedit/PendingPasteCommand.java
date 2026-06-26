package com.zxcmc.exort.integration.worldedit;

record PendingPasteCommand(
    boolean atOrigin, boolean onlySelect, long timestampMs, int usesRemaining) {
  PendingPasteCommand consume() {
    return new PendingPasteCommand(atOrigin, onlySelect, timestampMs, usesRemaining - 1);
  }
}
