package com.zxcmc.exort.core.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BreakAnimationStagesTest {
  @Test
  void stageIsFlooredAndClamped() {
    assertEquals(0, BreakAnimationStages.stageForProgress(-1.0));
    assertEquals(0, BreakAnimationStages.stageForProgress(Double.NaN));
    assertEquals(0, BreakAnimationStages.stageForProgress(0.0));
    assertEquals(3, BreakAnimationStages.stageForProgress(0.39));
    assertEquals(9, BreakAnimationStages.stageForProgress(0.99));
    assertEquals(9, BreakAnimationStages.stageForProgress(1.0));
    assertEquals(9, BreakAnimationStages.stageForProgress(10.0));
  }
}
