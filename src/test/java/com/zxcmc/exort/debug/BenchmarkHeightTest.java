package com.zxcmc.exort.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BenchmarkHeightTest {
  @Test
  void choosesNearTopOfNormalWorld() {
    assertEquals(305, BenchmarkHeight.blockY(-64, 320));
  }

  @Test
  void clampsToBottomMarginWhenWorldIsTooShort() {
    assertEquals(4, BenchmarkHeight.blockY(0, 10));
  }

  @Test
  void keepsConfiguredTopMarginWhenPossible() {
    assertEquals(241, BenchmarkHeight.blockY(0, 256));
  }
}
