package com.zxcmc.exort.sanity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkerSanityServiceTest {
  @Test
  void markedWireOnFullChorusIsMigratableWhenConfiguredCarrierDoesNotMatch() {
    assertTrue(MarkerCarrierSanity.validOrMigratableCarrier(false, true, false, true));
  }

  @Test
  void markedWireOnBarrierIsMigratableWhenConfiguredCarrierDoesNotMatch() {
    assertTrue(MarkerCarrierSanity.validOrMigratableCarrier(false, true, true, false));
  }

  @Test
  void unrelatedMaterialIsNotMigratable() {
    assertFalse(MarkerCarrierSanity.validOrMigratableCarrier(false, true, false, false));
  }

  @Test
  void nonWireMarkersDoNotUseWireCarrierMigrationException() {
    assertFalse(MarkerCarrierSanity.validOrMigratableCarrier(false, false, true, true));
  }
}
