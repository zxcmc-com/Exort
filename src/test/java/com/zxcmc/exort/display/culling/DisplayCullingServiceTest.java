package com.zxcmc.exort.display.culling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.display.core.DisplayRole;
import com.zxcmc.exort.display.core.DisplayTags;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DisplayCullingServiceTest {
  @Test
  void cullsAllExortDisplaysExceptBreakOverlays() {
    assertTrue(
        DisplayCullingService.isCullableDisplayTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.BUS_TAG)));
    assertTrue(
        DisplayCullingService.isCullableDisplayTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.WIRE_COMPACT_TAG)));
    assertTrue(DisplayCullingService.isCullableDisplayTags(Set.of(DisplayTags.DISPLAY_TAG)));
    assertTrue(
        DisplayCullingService.isCullableDisplayTags(Set.of(DisplayTags.DISPLAY_TAG, "storage")));
    assertTrue(
        DisplayCullingService.isCullableDisplayTags(Set.of(DisplayTags.DISPLAY_TAG, "terminal")));
    assertTrue(
        DisplayCullingService.isCullableDisplayTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.MONITOR_ITEM_TAG)));
    assertTrue(
        DisplayCullingService.isCullableDisplayTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.MONITOR_TEXT_TAG)));
    assertTrue(
        DisplayCullingService.isCullableDisplayTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.HOLOGRAM_TAG)));

    assertFalse(DisplayCullingService.isCullableDisplayTags(Set.of("storage")));
    assertFalse(
        DisplayCullingService.isCullableDisplayTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.BREAK_OVERLAY_TAG)));
  }

  @Test
  void classifiesDisplayRolesFromTags() {
    assertTrue(
        DisplayRole.fromTags(Set.of(DisplayTags.DISPLAY_TAG, "storage")) == DisplayRole.BLOCK);
    assertTrue(
        DisplayRole.fromTags(Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.WIRE_COMPACT_TAG))
            == DisplayRole.WIRE);
    assertTrue(
        DisplayRole.fromTags(Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.MONITOR_TEXT_TAG))
            == DisplayRole.MONITOR_CONTENT);
    assertTrue(
        DisplayRole.fromTags(Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.HOLOGRAM_TAG))
            == DisplayRole.HOLOGRAM);
    assertFalse(DisplayRole.fromTags(Set.of(DisplayTags.BREAK_OVERLAY_TAG)) != null);
  }

  @Test
  void paperFallbackOnlyHidesLowPriorityDisplaysUnderDensityPressure() {
    assertFalse(
        DisplayCullingService.shouldHideForDensity(
            true, true, DisplayRole.WIRE, false, false, 40.0, 64.0, 0.5));
    assertFalse(
        DisplayCullingService.shouldHideForDensity(
            false, true, DisplayRole.BLOCK, false, false, 40.0, 64.0, 0.5));
    assertFalse(
        DisplayCullingService.shouldHideForDensity(
            false, true, DisplayRole.WIRE, true, false, 40.0, 64.0, 0.5));
    assertTrue(
        DisplayCullingService.shouldHideForDensity(
            false, true, DisplayRole.WIRE, false, false, 40.0, 64.0, 0.5));
  }

  @Test
  void paperFallbackUsesDistanceHysteresisForHiddenDisplays() {
    assertTrue(
        DisplayCullingService.shouldHideForDensity(
            false, true, DisplayRole.WIRE, false, true, 30.0, 64.0, 0.5));
    assertFalse(
        DisplayCullingService.shouldHideForDensity(
            false, true, DisplayRole.WIRE, false, true, 27.0, 64.0, 0.5));
    assertFalse(
        DisplayCullingService.shouldHideForDensity(
            false, true, DisplayRole.WIRE, false, false, 30.0, 64.0, 0.5));
  }

  @Test
  void squaredDensityDecisionMatchesDistanceDecision() {
    assertEquals(
        DisplayCullingService.shouldHideForDensity(
            false, true, DisplayRole.WIRE, false, false, 40.0, 64.0, 0.5),
        DisplayCullingService.shouldHideForDensitySquared(
            false, true, DisplayRole.WIRE, false, false, 40.0 * 40.0, 64.0, 0.5));
    assertEquals(
        DisplayCullingService.shouldHideForDensity(
            false, true, DisplayRole.WIRE, false, true, 27.0, 64.0, 0.5),
        DisplayCullingService.shouldHideForDensitySquared(
            false, true, DisplayRole.WIRE, false, true, 27.0 * 27.0, 64.0, 0.5));
  }

  @Test
  void forwardRetentionKeepsFrontAndShortBehindBufferOnly() {
    assertTrue(DisplayCullingService.isWithinForwardRetention(1.0, 0.0, 20.0, 0.0));
    assertTrue(DisplayCullingService.isWithinForwardRetention(1.0, 0.0, 0.0, 20.0));
    assertTrue(DisplayCullingService.isWithinForwardRetention(1.0, 0.0, -4.0, 2.0));
    assertFalse(DisplayCullingService.isWithinForwardRetention(1.0, 0.0, -4.1, 0.0));
  }

  @Test
  void playerMotionKeepsDirectionWhileStoppedOrAfterSingleBackStep() {
    UUID worldId = UUID.randomUUID();
    DisplayCullingService.PlayerMotionState motion = new DisplayCullingService.PlayerMotionState();

    assertFalse(motion.sample(worldId, 0.0, 0.0).active());
    DisplayCullingService.MotionSnapshot forward = motion.sample(worldId, 1.0, 0.0);

    assertTrue(forward.active());
    assertTrue(forward.isForward(worldId, 1.0, 0.0, 16.0, 0.0));

    DisplayCullingService.MotionSnapshot stopped = motion.sample(worldId, 1.0, 0.0);
    assertTrue(stopped.active());
    assertEquals(forward.segmentId(), stopped.segmentId());
    assertTrue(stopped.isForward(worldId, 1.0, 0.0, 16.0, 0.0));

    DisplayCullingService.MotionSnapshot oneStepBack = motion.sample(worldId, 0.0, 0.0);
    assertTrue(oneStepBack.active());
    assertEquals(forward.segmentId(), oneStepBack.segmentId());
    assertTrue(oneStepBack.isForward(worldId, 0.0, 0.0, 16.0, 0.0));
  }

  @Test
  void playerMotionChangesDirectionAfterSustainedNewMovement() {
    UUID worldId = UUID.randomUUID();
    DisplayCullingService.PlayerMotionState motion = new DisplayCullingService.PlayerMotionState();

    motion.sample(worldId, 0.0, 0.0);
    long forwardSegment = motion.sample(worldId, 1.0, 0.0).segmentId();
    motion.sample(worldId, 0.0, 0.0);
    DisplayCullingService.MotionSnapshot beforeBuffer = motion.sample(worldId, -2.9, 0.0);

    assertEquals(forwardSegment, beforeBuffer.segmentId());
    assertTrue(beforeBuffer.isForward(worldId, -2.9, 0.0, 16.0, 0.0));

    DisplayCullingService.MotionSnapshot afterBuffer = motion.sample(worldId, -3.6, 0.0);
    assertNotEquals(forwardSegment, afterBuffer.segmentId());
    assertFalse(afterBuffer.isForward(worldId, -3.6, 0.0, 16.0, 0.0));
    assertTrue(afterBuffer.isForward(worldId, -3.6, 0.0, -16.0, 0.0));
  }
}
