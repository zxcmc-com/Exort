package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProtocolLibCompatibilityTest {
  @Test
  void parsesReleaseAndSnapshotVersions() {
    assertEquals(
        new ProtocolLibCompatibility.Version(5, 4, 0),
        ProtocolLibCompatibility.parseVersion("5.4.0").orElseThrow());
    assertEquals(
        new ProtocolLibCompatibility.Version(5, 5, 0),
        ProtocolLibCompatibility.parseVersion("5.5.0-SNAPSHOT-5a9afed").orElseThrow());
  }

  @Test
  void leavesUnknownVersionsUnparsed() {
    assertTrue(ProtocolLibCompatibility.parseVersion("dev-build").isEmpty());
    assertTrue(ProtocolLibCompatibility.parseVersion("").isEmpty());
  }

  @Test
  void recommendsUpdateOnlyBelowAdvisoryMinimum() {
    assertTrue(ProtocolLibCompatibility.isBelowRecommendedMinimum("1.21.11", "5.3.0"));
    assertFalse(ProtocolLibCompatibility.isBelowRecommendedMinimum("1.21.7", "5.4.0"));
    assertTrue(ProtocolLibCompatibility.isBelowRecommendedMinimum("26.1.2", "5.4.0"));
    assertFalse(
        ProtocolLibCompatibility.isBelowRecommendedMinimum("26.1.2", "5.5.0-SNAPSHOT-5a9afed"));
    assertFalse(ProtocolLibCompatibility.isBelowRecommendedMinimum("1.21.11", "dev-build"));
    assertFalse(ProtocolLibCompatibility.isBelowRecommendedMinimum("1.21.12", "5.3.0"));
    assertFalse(ProtocolLibCompatibility.isBelowRecommendedMinimum("27.0.0", "5.3.0"));
  }

  @Test
  void formatsActionableAndNeutralFailureAdvice() {
    assertEquals(
        "Update ProtocolLib to 5.4.0+ for Minecraft 1.21.11.",
        ProtocolLibCompatibility.failureAdvice("1.21.11", "5.3.0"));
    assertEquals(
        "This ProtocolLib build does not expose the required packet API; using fallback.",
        ProtocolLibCompatibility.failureAdvice("1.21.11", "5.4.0"));
  }
}
