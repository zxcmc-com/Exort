package com.zxcmc.exort.integration.worldedit;

record HistoryEntry(WorldEditMarkerHistory.FrameState state, long timestampMs) {
  MarkerSnapshot snapshot() {
    return state == null ? null : state.snapshot();
  }
}
