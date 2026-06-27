package com.zxcmc.exort.display.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkRefreshBudgetTest {
  @Test
  void hardCapIncludesStartNodeAndRejectsOverflow() {
    NetworkRefreshBudget budget = new NetworkRefreshBudget(2);
    budget.recordStartNode();

    assertTrue(budget.tryVisitNextNode());
    assertFalse(budget.tryVisitNextNode());

    assertEquals(1, budget.skipped());
  }

  @Test
  void hardCapIsAtLeastOneNode() {
    NetworkRefreshBudget budget = new NetworkRefreshBudget(0);
    budget.recordStartNode();

    assertFalse(budget.tryVisitNextNode());

    assertEquals(1, budget.skipped());
  }
}
