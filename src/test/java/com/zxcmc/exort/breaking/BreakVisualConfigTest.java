package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BreakVisualConfigTest {
  @Test
  void readsCurrentDefaults() {
    BreakVisualConfig config = BreakVisualConfig.fromConfig(new YamlConfiguration());

    assertTrue(config.vanillaParticles().enabled());
    assertParticleSettings(config.vanillaParticles().settings(), 16.0, 6, 30, 0.31);
    assertTrue(config.resourceOverlay().enabled());
    assertEquals("PAPER", config.resourceOverlay().displayBaseMaterial());
    assertEquals("breaking/stage_", config.resourceOverlay().modelPrefix());
    assertEquals(1.001, config.resourceOverlay().displayScale());
    assertTrue(config.resourceParticles().enabled());
    assertEquals("NETHERITE_BLOCK", config.resourceParticles().materialName());
    assertParticleSettings(config.resourceParticles().settings(), 16.0, 6, 30, 0.31);
  }

  @Test
  void readsConfiguredResourceVisuals() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("resourceMode.breakOverlay.enabled", false);
    yaml.set("resourceMode.breakOverlay.displayBaseMaterial", "DIAMOND");
    yaml.set("resourceMode.breakOverlay.modelPrefix", "custom/break_");
    yaml.set("resourceMode.breakOverlay.displayScale", 0.75);
    yaml.set("resourceMode.breakParticles.enabled", false);
    yaml.set("resourceMode.breakParticles.material", "STONE");
    yaml.set("resourceMode.breakParticles.range", 24.0);
    yaml.set("resourceMode.breakParticles.count", 8);
    yaml.set("resourceMode.breakParticles.breakCount", 40);
    yaml.set("resourceMode.breakParticles.spread", 0.2);

    BreakVisualConfig config = BreakVisualConfig.fromConfig(yaml);

    assertFalse(config.resourceOverlay().enabled());
    assertEquals("DIAMOND", config.resourceOverlay().displayBaseMaterial());
    assertEquals("custom/break_", config.resourceOverlay().modelPrefix());
    assertEquals(0.75, config.resourceOverlay().displayScale());
    assertFalse(config.resourceParticles().enabled());
    assertEquals("STONE", config.resourceParticles().materialName());
    assertParticleSettings(config.resourceParticles().settings(), 24.0, 8, 40, 0.2);
  }

  @Test
  void clampsNegativeParticleSettings() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("vanillaMode.breakParticles.range", -1.0);
    yaml.set("vanillaMode.breakParticles.count", -2);
    yaml.set("vanillaMode.breakParticles.breakCount", -3);
    yaml.set("vanillaMode.breakParticles.spread", -0.4);

    BreakVisualConfig config = BreakVisualConfig.fromConfig(yaml);

    assertParticleSettings(config.vanillaParticles().settings(), 0.0, 0, 0, 0.0);
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
