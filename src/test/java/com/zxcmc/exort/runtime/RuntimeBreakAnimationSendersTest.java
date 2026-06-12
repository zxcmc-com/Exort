package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.breaking.BreakType;
import com.zxcmc.exort.carrier.Carriers;
import org.junit.jupiter.api.Test;

class RuntimeBreakAnimationSendersTest {
  @Test
  void suppressesResourceWireStageParticlesOnlyForChorusCarrier() {
    assertFalse(
        RuntimeBreakAnimationSenders.shouldShowResourceStageParticles(
            Carriers.CHORUS_MATERIAL, BreakType.WIRE));
    assertTrue(
        RuntimeBreakAnimationSenders.shouldShowResourceStageParticles(
            Carriers.CARRIER_BARRIER, BreakType.WIRE));
    assertTrue(
        RuntimeBreakAnimationSenders.shouldShowResourceStageParticles(
            Carriers.CHORUS_MATERIAL, BreakType.STORAGE));
  }
}
