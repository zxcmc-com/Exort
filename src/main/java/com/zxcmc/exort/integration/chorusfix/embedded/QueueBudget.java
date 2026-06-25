package com.zxcmc.exort.integration.chorusfix.embedded;

public final class QueueBudget {
  private final int limit;
  private int used;

  public QueueBudget(int limit) {
    this.limit = Math.max(0, limit);
  }

  public boolean tryConsume() {
    if (used >= limit) {
      return false;
    }
    used++;
    return true;
  }

  public int used() {
    return used;
  }

  public int limit() {
    return limit;
  }
}
