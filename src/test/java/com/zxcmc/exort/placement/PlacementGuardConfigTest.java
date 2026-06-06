package com.zxcmc.exort.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PlacementGuardConfigTest {
  @Test
  void defaultsMatchCurrentPlacementGuardConfig() {
    PlacementGuardConfig config = PlacementGuardConfig.fromConfig(new YamlConfiguration());

    assertTrue(config.enabled());
    assertEquals(1, config.pollIntervalTicks());
    assertEquals(5, config.targetRangeBlocks());
    assertEquals(0.0625, config.guardScale());
    assertEquals(0.065, config.cornerInset());
    assertTrue(config.packetEventsGuardEnabled());
  }

  @Test
  void readsScalarEnabled() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("placementGuard", false);

    PlacementGuardConfig config = PlacementGuardConfig.fromConfig(yaml);

    assertFalse(config.enabled());
  }

  @Test
  void keepsGeometryHardcoded() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("placementGuard.pollIntervalTicks", 3);
    yaml.set("placementGuard.targetRangeBlocks", 7);
    yaml.set("placementGuard.guardScale", 0.125);
    yaml.set("placementGuard.cornerInset", 0.03);

    PlacementGuardConfig config = PlacementGuardConfig.fromConfig(yaml);

    assertEquals(1, config.pollIntervalTicks());
    assertEquals(5, config.targetRangeBlocks());
    assertEquals(0.0625, config.guardScale());
    assertEquals(0.065, config.cornerInset());
  }

  @Test
  void defaultGeometryOverlapsSingleChestShapeAtAllGuardCandidateExtremes() {
    PlacementGuardConfig config = PlacementGuardConfig.fromConfig(new YamlConfiguration());
    double guardScale = config.guardScale();
    double guardRadius = 0.125 * guardScale;
    double guardHeight = 0.9875 * guardScale;
    double inset = Math.min(0.45, config.cornerInset());
    double horizontalInset = Math.min(inset, Math.max(0.0, 0.5 - guardRadius));
    double verticalInset = Math.min(inset, Math.max(0.0, 1.0 - guardHeight));

    double lowX = guardRadius + horizontalInset;
    double highX = 1.0 - guardRadius - horizontalInset;
    double lowY = verticalInset;
    double highY = 1.0 - guardHeight - verticalInset;
    double lowZ = guardRadius + horizontalInset;
    double highZ = 1.0 - guardRadius - horizontalInset;

    double chestMin = 1.0 / 16.0;
    double chestMax = 15.0 / 16.0;
    double chestTop = 14.0 / 16.0;
    assertOverlaps(lowX - guardRadius, lowX + guardRadius, chestMin, chestMax);
    assertOverlaps(highX - guardRadius, highX + guardRadius, chestMin, chestMax);
    assertOverlaps(lowZ - guardRadius, lowZ + guardRadius, chestMin, chestMax);
    assertOverlaps(highZ - guardRadius, highZ + guardRadius, chestMin, chestMax);
    assertOverlaps(lowY, lowY + guardHeight, 0.0, chestTop);
    assertOverlaps(highY, highY + guardHeight, 0.0, chestTop);
  }

  private static void assertOverlaps(
      double firstMin, double firstMax, double secondMin, double secondMax) {
    assertTrue(firstMax >= secondMin && secondMax >= firstMin);
  }

  @Test
  void packetGuardOnlyUsesBasePacketEventsFlag() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("packetEvents.enabled", false);
    yaml.set("packetEvents.placementGuard.enabled", true);

    assertFalse(PlacementGuardConfig.fromConfig(yaml).packetEventsGuardEnabled());

    yaml.set("packetEvents.enabled", true);
    yaml.set("packetEvents.placementGuard.enabled", false);

    assertTrue(PlacementGuardConfig.fromConfig(yaml).packetEventsGuardEnabled());
  }
}
