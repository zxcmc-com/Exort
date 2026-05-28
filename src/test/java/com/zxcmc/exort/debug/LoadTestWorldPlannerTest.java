package com.zxcmc.exort.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class LoadTestWorldPlannerTest {
  @Test
  void buildPlanRejectsLaneWithoutEnoughCells() {
    LoadTestWorldPlanner.Plan plan =
        LoadTestWorldPlanner.buildPlan(Set.of(), LoadTestWorldPlanner.template().size() - 1);

    assertTrue(plan.placements().isEmpty());
  }

  @Test
  void buildPlanRejectsNonBenchmarkOccupiedCell() {
    LoadTestWorldPlanner.Cell occupied = LoadTestWorldPlanner.template().getFirst().cell();

    LoadTestWorldPlanner.Plan plan =
        LoadTestWorldPlanner.buildPlan(Set.of(occupied), LoadTestWorldPlanner.template().size());

    assertTrue(plan.placements().isEmpty());
  }

  @Test
  void sequenceCleanupCoversEveryTrackedCell() {
    LoadTestWorldPlanner.SequencePlan sequence = LoadTestWorldPlanner.sequencePlan(2, 10, Set.of());

    assertFalse(sequence.trackedCells().isEmpty());
    assertTrue(sequence.cleanupCells().containsAll(sequence.trackedCells()));
  }
}
