package com.zxcmc.exort.display.refresh;

final class NetworkRefreshBudget {
  private final int limit;
  private int visited;
  private int skipped;

  NetworkRefreshBudget(int hardCap) {
    this.limit = Math.max(1, hardCap);
  }

  void recordStartNode() {
    visited = Math.min(limit, 1);
  }

  boolean tryVisitNextNode() {
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
