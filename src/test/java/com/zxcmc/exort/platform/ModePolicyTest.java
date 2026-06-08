package com.zxcmc.exort.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.carrier.WireCarrierMode;
import org.junit.jupiter.api.Test;

class ModePolicyTest {
  @Test
  void resourceModeUsesChorusCarrierWhenConfiguredAndPaperAllowsIt() {
    ModePolicy policy = ModePolicy.evaluate("RESOURCE", true, WireCarrierMode.CHORUS_PLANT);

    assertEquals("RESOURCE", policy.configuredMode());
    assertTrue(policy.resourceMode());
    assertFalse(policy.resourceWireUsesBarrier());
    assertFalse(policy.resourceWireCarrierFallback());
    assertFalse(policy.unknownMode());
  }

  @Test
  void resourceModeFallsBackToBarrierCarrierWhenConfiguredChorusButPaperBlocksIt() {
    ModePolicy policy = ModePolicy.evaluate("RESOURCE", false, WireCarrierMode.CHORUS_PLANT);

    assertEquals("RESOURCE", policy.configuredMode());
    assertTrue(policy.resourceMode());
    assertTrue(policy.resourceWireUsesBarrier());
    assertTrue(policy.resourceWireCarrierFallback());
    assertFalse(policy.unknownMode());
  }

  @Test
  void explicitBarrierCarrierDoesNotNeedFallbackWarningWhenPaperBlocksChorus() {
    ModePolicy policy = ModePolicy.evaluate("RESOURCE", false, WireCarrierMode.BARRIER);

    assertEquals("RESOURCE", policy.configuredMode());
    assertTrue(policy.resourceMode());
    assertTrue(policy.resourceWireUsesBarrier());
    assertFalse(policy.resourceWireCarrierFallback());
    assertFalse(policy.unknownMode());
  }

  @Test
  void explicitBarrierCarrierDoesNotNeedFallbackWarningWhenPaperAllowsChorus() {
    ModePolicy policy = ModePolicy.evaluate("RESOURCE", true, WireCarrierMode.BARRIER);

    assertEquals("RESOURCE", policy.configuredMode());
    assertTrue(policy.resourceMode());
    assertTrue(policy.resourceWireUsesBarrier());
    assertFalse(policy.resourceWireCarrierFallback());
    assertFalse(policy.unknownMode());
  }

  @Test
  void vanillaModeUsesBarrierCarrierWithoutResourceFallbackWarning() {
    ModePolicy policy = ModePolicy.evaluate("VANILLA", false, WireCarrierMode.CHORUS_PLANT);

    assertEquals("VANILLA", policy.configuredMode());
    assertFalse(policy.resourceMode());
    assertFalse(policy.resourceWireUsesBarrier());
    assertFalse(policy.resourceWireCarrierFallback());
    assertFalse(policy.unknownMode());
  }

  @Test
  void unknownModeUsesResourceDefault() {
    ModePolicy policy = ModePolicy.evaluate("custom", true, WireCarrierMode.CHORUS_PLANT);

    assertEquals("RESOURCE", policy.configuredMode());
    assertTrue(policy.resourceMode());
    assertFalse(policy.resourceWireUsesBarrier());
    assertFalse(policy.resourceWireCarrierFallback());
    assertTrue(policy.unknownMode());
  }
}
