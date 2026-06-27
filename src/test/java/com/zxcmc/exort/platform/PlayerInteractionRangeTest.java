package com.zxcmc.exort.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlayerInteractionRangeTest {
  @Test
  void nullPlayerUsesTheSameFallbackAsInvalidAttributeValues() {
    double fallback = PlayerInteractionRange.normalizeBlockInteractionRange(Double.NaN);

    assertEquals(fallback, PlayerInteractionRange.blockInteractionRange(null));
    assertEquals(
        PlayerInteractionRange.physicalDeviceCloseRange(fallback),
        PlayerInteractionRange.physicalDeviceCloseRange(null));
  }

  @Test
  void physicalDeviceCloseRangeStaysAboveNormalizedInteractionRange() {
    double normalized = PlayerInteractionRange.normalizeBlockInteractionRange(6.0D);

    assertEquals(6.0D, normalized);
    assertTrue(PlayerInteractionRange.physicalDeviceCloseRange(normalized) > normalized);
  }

  @Test
  void physicalDeviceCloseRangeFallsBackBeforeAddingSafetyBuffer() {
    assertEquals(
        PlayerInteractionRange.physicalDeviceCloseRange(Double.NaN),
        PlayerInteractionRange.physicalDeviceCloseRange(0.0D));
  }
}
