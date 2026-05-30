package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ExortBlockProxyServiceTest {
  private static final DisplayCullingConfig.BlockProxyConfig CONFIG =
      new DisplayCullingConfig.BlockProxyConfig(
              true, Material.NETHERITE_BLOCK, 64.0, 2.0, 6.0, 8.0, 1200)
          .normalized();

  @Test
  void proxiesOnlyAfterEnterBuffer() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(false, 71.9, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 72.0, 1.0, CONFIG));
  }

  @Test
  void restoresProxiedBlocksBeforeDisplayCanRenderAgain() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(true, 70.1, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 70.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 64.0, 1.0, CONFIG));
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
        ExortBlockProxyService.decideVisual(false, 52.7, 0.7, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 52.8, 0.7, CONFIG));
  }

  @Test
  void keepsHysteresisBandStable() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(false, 71.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(true, 71.0, 1.0, CONFIG));
  }

  @Test
  void clampsUnsafeMultiplier() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 12.0, -1.0, CONFIG));
  }
}
