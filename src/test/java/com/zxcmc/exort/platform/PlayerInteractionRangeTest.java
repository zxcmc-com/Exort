package com.zxcmc.exort.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlayerInteractionRangeTest {
  @Test
  void physicalDeviceCloseRangeAddsSafetyBufferToFallback() {
    assertEquals(8.0D, PlayerInteractionRange.blockInteractionRange(null));
    assertEquals(12.0D, PlayerInteractionRange.physicalDeviceCloseRange(null));
    assertEquals(144.0D, PlayerInteractionRange.physicalDeviceCloseRangeSquared(null));
  }

  @Test
  void physicalDeviceCloseRangeAddsSafetyBufferToInteractionRange() {
    assertEquals(6.0D, PlayerInteractionRange.normalizeBlockInteractionRange(6.0D));
    assertEquals(10.0D, PlayerInteractionRange.physicalDeviceCloseRange(6.0D));
  }

  @Test
  void physicalDeviceCloseRangeFallsBackBeforeAddingSafetyBuffer() {
    assertEquals(8.0D, PlayerInteractionRange.normalizeBlockInteractionRange(Double.NaN));
    assertEquals(8.0D, PlayerInteractionRange.normalizeBlockInteractionRange(0.0D));
    assertEquals(12.0D, PlayerInteractionRange.physicalDeviceCloseRange(Double.NaN));
    assertEquals(12.0D, PlayerInteractionRange.physicalDeviceCloseRange(0.0D));
  }
}
