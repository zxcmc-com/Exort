package com.zxcmc.exort.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExortPluginModeWarningTest {
  @Test
  void resourceBarrierCarrierWarningMentionsFixCommandAndHitbox() {
    String warning = String.join("\n", ExortPlugin.resourceWireCarrierWarningLines());

    assertTrue(warning.contains(ExortPlugin.MODE_FIX_RESOURCE_COMMAND));
    assertTrue(warning.contains("wire visuals stay RESOURCE"));
    assertTrue(warning.contains("wire hitboxes are full blocks"));
    assertTrue(warning.contains("enable chorus carriers after restart"));
    assertFalse(warning.contains("effective mode is VANILLA"));
  }
}
