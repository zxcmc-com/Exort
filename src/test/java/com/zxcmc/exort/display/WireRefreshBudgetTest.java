package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WireRefreshBudgetTest {
  @Test
  void hardCapIncludesStartWireAndRejectsOverflow() {
    WireRefreshBudget budget = new WireRefreshBudget(2);
    budget.recordStartWire();

    assertTrue(budget.tryVisitNextWire());
    assertFalse(budget.tryVisitNextWire());

    assertEquals(1, budget.skipped());
  }

  @Test
  void hardCapIsAtLeastOneWire() {
    WireRefreshBudget budget = new WireRefreshBudget(0);
    budget.recordStartWire();

    assertFalse(budget.tryVisitNextWire());

    assertEquals(1, budget.skipped());
  }
}
