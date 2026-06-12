package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreakParticleSenderTest {
  @Test
  void vanillaSenderUsesTypeAwareParticleBlockData() {
    BreakParticleSender sender =
        BreakParticleSender.vanilla(null, new BreakParticleSender.Settings(16.0, 6, 30, 0.31));

    assertTrue(sender.usesTypeAwareParticleBlockData());
    assertTrue(sender.showsStageParticlesFor(BreakType.WIRE));
  }

  @Test
  void resourceSenderUsesProvidedParticleBlockDataResolver() {
    BreakParticleSender sender =
        BreakParticleSender.resource(
            null, new BreakParticleSender.Settings(16.0, 6, 30, 0.31), (block, type) -> null);

    assertFalse(sender.usesTypeAwareParticleBlockData());
    assertTrue(sender.showsStageParticlesFor(BreakType.WIRE));
  }

  @Test
  void resourceSenderWithMissingResolverDoesNotUseTypeAwareParticleBlockData() {
    BreakParticleSender sender =
        BreakParticleSender.resource(
            null,
            new BreakParticleSender.Settings(16.0, 6, 30, 0.31),
            (BreakParticleSender.BlockDataResolver) null);

    assertFalse(sender.usesTypeAwareParticleBlockData());
  }

  @Test
  void resourceSenderCanSuppressStageParticlesByType() {
    BreakParticleSender sender =
        BreakParticleSender.resource(
            null,
            new BreakParticleSender.Settings(16.0, 6, 30, 0.31),
            (block, type) -> null,
            type -> type != BreakType.WIRE);

    assertFalse(sender.showsStageParticlesFor(BreakType.WIRE));
    assertTrue(sender.showsStageParticlesFor(BreakType.STORAGE));
  }
}
