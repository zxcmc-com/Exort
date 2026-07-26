package com.zxcmc.exort.integration.worldedit;

record PendingStackPatch(PendingPastePatch patch, long timestampMs, int usesRemaining) {
  PendingStackPatch consume() {
    return new PendingStackPatch(patch, timestampMs, usesRemaining - 1);
  }
}
