package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreakTimingPolicyTest {
  @Test
  void skipsSameAndFirstTickNonInstantDamage() {
    long startedTick = 10L;

    assertFalse(BreakTimingPolicy.canApplyDamage(startedTick, startedTick, 0.2));
    assertFalse(BreakTimingPolicy.canApplyDamage(startedTick + 1L, startedTick, 0.2));
    assertTrue(BreakTimingPolicy.canApplyDamage(startedTick + 2L, startedTick, 0.2));
  }

  @Test
  void instantDamageBypassesFirstTickDelay() {
    assertTrue(BreakTimingPolicy.canApplyDamage(10L, 10L, 1.0));
  }

  @Test
  void hitSoundIsThrottledToFourTicks() {
    assertTrue(BreakTimingPolicy.canPlayHitSound(20L, Long.MIN_VALUE));
    assertFalse(BreakTimingPolicy.canPlayHitSound(23L, 20L));
    assertTrue(BreakTimingPolicy.canPlayHitSound(24L, 20L));
  }
}
