package com.zxcmc.exort.core.sanity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkerSanityServiceTest {
  @Test
  void markedWireOnFullChorusIsMigratableWhenConfiguredCarrierDoesNotMatch() {
    assertTrue(MarkerSanityService.validOrMigratableCarrier(false, true, false, true));
  }

  @Test
  void markedWireOnBarrierIsMigratableWhenConfiguredCarrierDoesNotMatch() {
    assertTrue(MarkerSanityService.validOrMigratableCarrier(false, true, true, false));
  }

  @Test
  void unrelatedMaterialIsNotMigratable() {
    assertFalse(MarkerSanityService.validOrMigratableCarrier(false, true, false, false));
  }

  @Test
  void nonWireMarkersDoNotUseWireCarrierMigrationException() {
    assertFalse(MarkerSanityService.validOrMigratableCarrier(false, false, true, true));
  }
}
