package com.zxcmc.exort.display.culling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.display.core.DisplayRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptiveViewRangeStateTest {
  private static final DisplayCullingConfig.AdaptiveViewRangeConfig CONFIG =
      new DisplayCullingConfig.AdaptiveViewRangeConfig(
              true,
              List.of(320, 640, 1200),
              List.of(240, 520, 960),
              3,
              60,
              40,
              200,
              List.of(1.0, 0.9, 0.8, 0.7),
              List.of(0.5, 0.35, 0.25, 0.12),
              List.of(0.45, 0.3, 0.2, 0.1),
              List.of(0.35, 0.25, 0.15, 0.1))
          .normalized();

  @Test
  void stepsDownOnSustainedDensity() {
    AdaptiveViewRangeState state = new AdaptiveViewRangeState(CONFIG);

    assertFalse(state.update(320, 1, 10));
    assertFalse(state.update(320, 2, 10));
    assertTrue(state.update(320, 3, 10));

    assertEquals(1, state.levelIndex());
    assertEquals(0.35, CONFIG.rangeMultiplier(DisplayRole.WIRE, state.levelIndex()));
  }

  @Test
  void highDensityStepsDownWithCooldown() {
    AdaptiveViewRangeState state = new AdaptiveViewRangeState(CONFIG);

    assertFalse(state.update(320, 1, 10));
    assertFalse(state.update(320, 2, 10));
    assertTrue(state.update(320, 3, 10));
    assertFalse(state.update(640, 4, 10));
    assertEquals(1, state.levelIndex());

    assertFalse(state.update(640, 5, 10));
    assertFalse(state.update(640, 6, 10));
    assertTrue(state.update(640, 7, 10));
    assertEquals(2, state.levelIndex());
    assertEquals(0.25, CONFIG.rangeMultiplier(DisplayRole.WIRE, state.levelIndex()));
  }

  @Test
  void recoversOnlyAfterStableWindow() {
    AdaptiveViewRangeState state =
        new AdaptiveViewRangeState(
            new DisplayCullingConfig.AdaptiveViewRangeConfig(
                    true,
                    List.of(160),
                    List.of(120),
                    1,
                    3,
                    1,
                    1,
                    List.of(1.0, 0.75),
                    List.of(0.5, 0.35),
                    List.of(0.45, 0.3),
                    List.of(0.35, 0.25))
                .normalized());

    assertTrue(state.update(160, 1, 10));
    assertFalse(state.update(100, 2, 10));
    assertFalse(state.update(100, 3, 10));
    assertTrue(state.update(100, 4, 10));
    assertEquals(0, state.levelIndex());
  }

  @Test
  void disabledStateNeverChanges() {
    AdaptiveViewRangeState state =
        new AdaptiveViewRangeState(
            new DisplayCullingConfig.AdaptiveViewRangeConfig(
                    false,
                    List.of(1),
                    List.of(0),
                    1,
                    1,
                    1,
                    1,
                    List.of(1.0, 0.5),
                    List.of(1.0, 0.5),
                    List.of(1.0, 0.5),
                    List.of(1.0, 0.5))
                .normalized());

    assertFalse(state.update(999, 1, 1));
    assertEquals(0, state.levelIndex());
  }
}
