package com.zxcmc.exort.breaking;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

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

  public static BreakVisualConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new BreakVisualConfig(
        readParticleConfig(config, "vanillaMode.breakParticles"),
        readResourceOverlayConfig(config),
        readResourceParticleConfig(config));
  }

  private static ParticleConfig readParticleConfig(ConfigurationSection config, String path) {
    return new ParticleConfig(
        config.getBoolean(path + ".enabled", true), readParticleSettings(config, path));
  }

  private static ResourceOverlayConfig readResourceOverlayConfig(ConfigurationSection config) {
    String path = "resourceMode.breakOverlay";
    return new ResourceOverlayConfig(
        config.getBoolean(path + ".enabled", true),
        config.getString(path + ".displayBaseMaterial", "PAPER"),
        config.getString(path + ".modelPrefix", "breaking/stage_"),
        config.getDouble(path + ".displayScale", 1.001));
  }

  private static ResourceParticleConfig readResourceParticleConfig(ConfigurationSection config) {
    String path = "resourceMode.breakParticles";
    return new ResourceParticleConfig(
        config.getBoolean(path + ".enabled", true),
        readParticleSettings(config, path),
        config.getString(path + ".material", "NETHERITE_BLOCK"));
  }

  private static BreakParticleSender.Settings readParticleSettings(
      ConfigurationSection config, String path) {
    double range = Math.max(0.0, config.getDouble(path + ".range", DEFAULT_RANGE));
    int stageCount = Math.max(0, config.getInt(path + ".count", DEFAULT_STAGE_PARTICLES));
    int breakCount = Math.max(0, config.getInt(path + ".breakCount", DEFAULT_BREAK_PARTICLES));
    double spread = Math.max(0.0, config.getDouble(path + ".spread", DEFAULT_SPREAD));
    return new BreakParticleSender.Settings(range, stageCount, breakCount, spread);
  }

  public record ParticleConfig(boolean enabled, BreakParticleSender.Settings settings) {
    public ParticleConfig {
      Objects.requireNonNull(settings, "settings");
    }
  }

  public record ResourceOverlayConfig(
      boolean enabled, String displayBaseMaterial, String modelPrefix, double displayScale) {}

  public record ResourceParticleConfig(
      boolean enabled, BreakParticleSender.Settings settings, String materialName) {
    public ResourceParticleConfig {
      Objects.requireNonNull(settings, "settings");
    }
  }
}
