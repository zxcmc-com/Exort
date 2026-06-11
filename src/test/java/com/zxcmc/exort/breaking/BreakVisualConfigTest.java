package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreakVisualConfigTest {
  @Test
  void defaultsMatchCurrentBreakVisuals() {
    BreakVisualConfig config = BreakVisualConfig.defaults();

    assertTrue(config.vanillaParticles().enabled());
    assertParticleSettings(config.vanillaParticles().settings(), 16.0, 6, 30, 0.31);
    assertTrue(config.resourceOverlay().enabled());
    assertEquals("PAPER", config.resourceOverlay().displayBaseMaterial());
    assertEquals("breaking/", config.resourceOverlay().modelRoot());
    assertEquals(1.001, config.resourceOverlay().displayScale());
    assertTrue(config.resourceParticles().enabled());
    assertParticleSettings(config.resourceParticles().settings(), 16.0, 6, 30, 0.31);
  }

  private static void assertParticleSettings(
      BreakParticleSender.Settings settings,
      double range,
      int stageCount,
      int breakCount,
      double spread) {
    assertEquals(range, settings.range());
    assertEquals(stageCount, settings.stageCount());
    assertEquals(breakCount, settings.breakCount());
    assertEquals(spread, settings.spread());
  }
}
