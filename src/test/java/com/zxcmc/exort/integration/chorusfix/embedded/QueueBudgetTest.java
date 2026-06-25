package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class QueueBudgetTest {
  @Test
  void acceptsOnlyConfiguredNumberOfEntries() {
    QueueBudget budget = new QueueBudget(2);

    assertTrue(budget.tryConsume());
    assertTrue(budget.tryConsume());
    assertFalse(budget.tryConsume());
    assertEquals(2, budget.used());
  }

  @Test
  void treatsNegativeLimitAsZero() {
    QueueBudget budget = new QueueBudget(-1);

    assertFalse(budget.tryConsume());
    assertEquals(0, budget.limit());
  }
}
