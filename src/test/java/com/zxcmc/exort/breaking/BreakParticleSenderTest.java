package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreakParticleSenderTest {
  @Test
  void resourceSenderUsesTypeAwareParticleBlockDataByDefault() {
    BreakParticleSender sender =
        BreakParticleSender.resource(null, new BreakParticleSender.Settings(16.0, 6, 30, 0.31));

    assertTrue(sender.usesTypeAwareParticleBlockData());
  }
}
