package com.zxcmc.exort.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoadTestArithmeticTest {
  @Test
  void syntheticIterationsPreserveNormalCost() {
    assertEquals(150_000, LoadTestService.boundedSyntheticIterations(500, 1, 300));
  }

  @Test
  void syntheticIterationsSaturateInsteadOfOverflowingOrRunningUnbounded() {
    assertEquals(
        LoadTestService.MAX_SYNTHETIC_CPU_ITERATIONS_PER_TICK,
        LoadTestService.boundedSyntheticIterations(
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
  }

  @Test
  void syntheticIterationsAlwaysDoAtLeastOneIteration() {
    assertEquals(1, LoadTestService.boundedSyntheticIterations(-1, 0, 0));
  }
}
