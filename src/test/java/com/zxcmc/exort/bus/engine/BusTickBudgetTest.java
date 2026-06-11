package com.zxcmc.exort.bus.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.bus.BusPos;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusTickBudgetTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void globalBudgetCountsDueAttemptsEvenWithoutMoves() {
    BusTickBudget budget = new BusTickBudget(2, 0);

    assertTrue(budget.hasGlobalBudget());
    budget.recordDueAttempt();
    assertTrue(budget.hasGlobalBudget());
    budget.recordDueAttempt();

    assertFalse(budget.hasGlobalBudget());
  }

  @Test
  void chunkBudgetCountsAttemptsEvenWithoutMoves() {
    BusTickBudget budget = new BusTickBudget(10, 1);
    BusPos first = new BusPos(WORLD, 1, 64, 1);
    BusPos sameChunk = new BusPos(WORLD, 15, 64, 15);
    BusPos otherChunk = new BusPos(WORLD, 16, 64, 1);

    assertFalse(budget.isChunkBudgetReached(first));
    budget.recordChunkAttempt(first);

    assertTrue(budget.isChunkBudgetReached(sameChunk));
    assertFalse(budget.isChunkBudgetReached(otherChunk));
  }
}
