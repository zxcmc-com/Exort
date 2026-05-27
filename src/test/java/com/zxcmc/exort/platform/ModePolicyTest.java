package com.zxcmc.exort.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModePolicyTest {
  @Test
  void resourceModeStaysResourceWhenChorusUpdatesAreDisabled() {
    ModePolicy policy = ModePolicy.evaluate("RESOURCE", true);

    assertEquals("RESOURCE", policy.configuredMode());
    assertTrue(policy.resourceMode());
    assertEquals("", policy.fallbackReason());
    assertFalse(policy.unknownMode());
  }

  @Test
  void resourceModeFallsBackToVanillaWhenChorusUpdatesAreEnabled() {
    ModePolicy policy = ModePolicy.evaluate("RESOURCE", false);

    assertEquals("RESOURCE", policy.configuredMode());
    assertFalse(policy.resourceMode());
    assertEquals(ModePolicy.CHORUS_FALLBACK_REASON, policy.fallbackReason());
    assertFalse(policy.unknownMode());
  }

  @Test
  void unknownModeUsesResourceDefault() {
    ModePolicy policy = ModePolicy.evaluate("custom", true);

    assertEquals("RESOURCE", policy.configuredMode());
    assertTrue(policy.resourceMode());
    assertTrue(policy.unknownMode());
  }
}
