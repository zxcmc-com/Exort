package com.zxcmc.exort.wireless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WirelessRuntimeConfigTest {
  @Test
  void readsConfiguredValues() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wireless.enabled", false);
    yaml.set("wireless.rangeBlocks", 96);

    WirelessRuntimeConfig config = WirelessRuntimeConfig.fromConfig(yaml);

    assertFalse(config.enabled());
    assertEquals(96, config.rangeBlocks());
  }

  @Test
  void clampsNegativeRange() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wireless.rangeBlocks", -2);

    WirelessRuntimeConfig config = WirelessRuntimeConfig.fromConfig(yaml);

    assertEquals(0, config.rangeBlocks());
  }

  @Test
  void clampsExtremeRangeBeforeSpatialIndexArithmetic() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wireless.rangeBlocks", Integer.MAX_VALUE);

    WirelessRuntimeConfig config = WirelessRuntimeConfig.fromConfig(yaml);

    assertEquals(WirelessRuntimeConfig.MAX_RANGE_BLOCKS, config.rangeBlocks());
  }

  @Test
  void defaultBoosterMultipliersProduceExpectedRangesAndGlobalImmortal() {
    WirelessRuntimeConfig config = WirelessRuntimeConfig.fromConfig(new YamlConfiguration());

    assertEquals(72, config.effectiveRangeBlocks(WirelessBoosterTier.RARE));
    assertEquals(96, config.effectiveRangeBlocks(WirelessBoosterTier.MYTHICAL));
    assertEquals(288, config.effectiveRangeBlocks(WirelessBoosterTier.LEGENDARY));
    assertEquals(-1, config.effectiveRangeBlocks(WirelessBoosterTier.IMMORTAL));
    assertTrue(config.isGlobal(WirelessBoosterTier.IMMORTAL));
    assertEquals(288, config.maxFiniteRangeBlocks());
  }

  @Test
  void configuredMultipliersRoundClampAndSanitizeInvalidValues() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wireless.rangeBlocks", 49);
    yaml.set("wireless.boosters.rangeMultipliers.rare", 1.5D);
    yaml.set("wireless.boosters.rangeMultipliers.mythical", -2.0D);
    yaml.set("wireless.boosters.rangeMultipliers.legendary", Double.POSITIVE_INFINITY);
    yaml.set("wireless.boosters.rangeMultipliers.immortal", 100_000.0D);

    WirelessRuntimeConfig config = WirelessRuntimeConfig.fromConfig(yaml);

    assertEquals(74, config.effectiveRangeBlocks(WirelessBoosterTier.RARE));
    assertEquals(0, config.effectiveRangeBlocks(WirelessBoosterTier.MYTHICAL));
    assertEquals(294, config.effectiveRangeBlocks(WirelessBoosterTier.LEGENDARY));
    assertEquals(
        WirelessRuntimeConfig.MAX_RANGE_BLOCKS,
        config.effectiveRangeBlocks(WirelessBoosterTier.IMMORTAL));
  }
}
