package com.zxcmc.exort.display;

final class WireRefreshBudget {
  private final int limit;
  private int visited;
  private int skipped;

  WireRefreshBudget(int hardCap) {
    this.limit = Math.max(1, hardCap);
  }

  void recordStartWire() {
    visited = Math.min(limit, 1);
  }

  boolean tryVisitNextWire() {
    if (visited >= limit) {
      skipped++;
      return false;
    }
    visited++;
    return true;
  }

  int skipped() {
    return skipped;
  }
}
