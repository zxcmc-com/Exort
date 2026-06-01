package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ExortBlockProxyServiceTest {
  private static final DisplayCullingConfig.BlockProxyConfig CONFIG =
      new DisplayCullingConfig.BlockProxyConfig(
              true, Material.NETHERITE_BLOCK, 64.0, 2.0, 6.0, 8.0, 1200)
          .normalized();

  @Test
  void proxiesAtSingleTransitionBeforeRenderEdge() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(false, 61.9, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 62.0, 1.0, CONFIG));
  }

  @Test
  void restoresProxiedBlocksInsideDisplayRenderRange() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(true, 62.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 61.9, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 8.0, 1.0, CONFIG));
  }

  @Test
  void forceRealDistanceWinsOverProxyState() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 8.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(false, 8.0, 1.0, CONFIG));
  }

  @Test
  void usesLowerBlockViewRangeMultiplier() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(false, 42.7, 0.7, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 42.8, 0.7, CONFIG));
  }

  @Test
  void realBlocksDoNotStayRealInsideTransitionBand() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 63.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(true, 63.0, 1.0, CONFIG));
  }

  @Test
  void clampsUnsafeMultiplier() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 12.0, -1.0, CONFIG));
  }

  @Test
  void skipsProxyChangeWhenChunkWasNotSentToPlayer() {
    assertFalse(ExortBlockProxyService.shouldPrepareChange(false, false, true, true));
  }

  @Test
  void allowsProxyChangeOnlyForSentTrackedCandidates() {
    assertTrue(ExortBlockProxyService.shouldPrepareChange(true, false, true, true));
    assertFalse(ExortBlockProxyService.shouldPrepareChange(true, false, false, true));
    assertFalse(ExortBlockProxyService.shouldPrepareChange(true, false, true, false));
  }

  @Test
  void skipsRestoreChangeWhenChunkWasNotSentToPlayer() {
    assertFalse(ExortBlockProxyService.shouldPrepareChange(false, true, false, false));
    assertTrue(ExortBlockProxyService.shouldPrepareChange(true, true, false, false));
  }

  @Test
  void proxyDistanceIncludesPlayerHeight() {
    double distance = ExortBlockProxyService.visualDistanceToBlock(0.5, 62.5, 0.5, 0, 0, 0);
    assertEquals(62.0, distance, 0.0001);
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, distance, 1.0, CONFIG));
  }

  @Test
  void restoreBufferDoesNotMoveTransitionFarInsideVisibleRange() {
    DisplayCullingConfig.BlockProxyConfig config =
        new DisplayCullingConfig.BlockProxyConfig(
                true, Material.NETHERITE_BLOCK, 64.0, 2.0, 12.0, 8.0, 1200)
            .normalized();
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(false, 61.9, 1.0, config));
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 62.0, 1.0, config));
  }
}
