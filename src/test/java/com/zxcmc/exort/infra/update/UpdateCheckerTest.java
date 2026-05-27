package com.zxcmc.exort.infra.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UpdateCheckerTest {
  @Test
  void extractsGradleVersionDeclaration() {
    String buildGradle =
        """
        group = 'com.zxcmc.exort'
        version = '0.11.5'
        description = 'Exort Storage Network'
        """;

    assertEquals("0.11.5", UpdateChecker.extractVersion(buildGradle).orElseThrow());
  }

  @Test
  void returnsEmptyWhenVersionDeclarationIsMissing() {
    assertTrue(UpdateChecker.extractVersion("group = 'com.zxcmc.exort'").isEmpty());
  }

  @Test
  void comparesNumericVersionParts() throws Exception {
    assertTrue(UpdateChecker.compareVersions("0.11.4", "0.11.5") < 0);
    assertEquals(0, UpdateChecker.compareVersions("0.11.5", "0.11.5"));
    assertTrue(UpdateChecker.compareVersions("0.11.6", "0.11.5") > 0);
    assertEquals(0, UpdateChecker.compareVersions("v0.11.5", "0.11.5"));
  }

  @Test
  void treatsReleaseAsNewerThanSameNumericSnapshot() throws Exception {
    assertTrue(UpdateChecker.compareVersions("0.11.5-SNAPSHOT", "0.11.5") < 0);
  }
}
