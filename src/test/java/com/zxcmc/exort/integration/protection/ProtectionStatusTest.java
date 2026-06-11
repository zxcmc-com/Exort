package com.zxcmc.exort.integration.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtectionStatusTest {
  @Test
  void runtimeFailuresDegradeActiveStatus() {
    ProtectionStatus status =
        ProtectionStatus.active(false, List.of("WorldGuard"), List.of("Towny"), List.of())
            .withRuntimeFailures(List.of("WorldGuard:build"));

    assertEquals(ProtectionStatus.Mode.DEGRADED, status.mode());
    assertTrue(status.degraded());
    assertEquals(List.of("WorldGuard:build"), status.runtimeFailures());
  }
}
