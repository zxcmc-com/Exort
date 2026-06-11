package com.zxcmc.exort.breaking;

import java.util.Objects;

public record BreakVisualConfig(
    ParticleConfig vanillaParticles,
    ResourceOverlayConfig resourceOverlay,
    ResourceParticleConfig resourceParticles) {
  private static final double DEFAULT_RANGE = 16.0;
  private static final int DEFAULT_STAGE_PARTICLES = 6;
  private static final int DEFAULT_BREAK_PARTICLES = 30;
  private static final double DEFAULT_SPREAD = 0.31;

  public BreakVisualConfig {
    Objects.requireNonNull(vanillaParticles, "vanillaParticles");
    Objects.requireNonNull(resourceOverlay, "resourceOverlay");
    Objects.requireNonNull(resourceParticles, "resourceParticles");
  }

  public static BreakVisualConfig defaults() {
    return new BreakVisualConfig(
        particleConfig(true),
        new ResourceOverlayConfig(true, "PAPER", "breaking/", 1.001),
        new ResourceParticleConfig(true, defaultParticleSettings()));
  }

  private static ParticleConfig particleConfig(boolean enabled) {
    return new ParticleConfig(enabled, defaultParticleSettings());
  }

  private static BreakParticleSender.Settings defaultParticleSettings() {
    return new BreakParticleSender.Settings(
        DEFAULT_RANGE, DEFAULT_STAGE_PARTICLES, DEFAULT_BREAK_PARTICLES, DEFAULT_SPREAD);
  }

  public record ParticleConfig(boolean enabled, BreakParticleSender.Settings settings) {
    public ParticleConfig {
      Objects.requireNonNull(settings, "settings");
    }
  }

  public record ResourceOverlayConfig(
      boolean enabled, String displayBaseMaterial, String modelRoot, double displayScale) {}

  public record ResourceParticleConfig(boolean enabled, BreakParticleSender.Settings settings) {
    public ResourceParticleConfig {
      Objects.requireNonNull(settings, "settings");
    }
  }
}
