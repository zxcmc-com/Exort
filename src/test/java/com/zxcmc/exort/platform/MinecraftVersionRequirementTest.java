package com.zxcmc.exort.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinecraftVersionRequirementTest {
  private final MinecraftVersionRequirement requirement =
      MinecraftVersionRequirement.atLeast(1, 21, 7);

  @Test
  void parsesPatchVersionsWithSuffixes() {
    var version = MinecraftVersionRequirement.Version.parse("1.21.11-pre1").orElseThrow();

    assertTrue(requirement.accepts(version));
  }

  @Test
  void rejectsVersionsBelowMinimumPatch() {
    var version = MinecraftVersionRequirement.Version.parse("1.21.6").orElseThrow();

    assertFalse(requirement.accepts(version));
  }

  @Test
  void rejectsInvalidVersions() {
    assertTrue(MinecraftVersionRequirement.Version.parse("dev-build").isEmpty());
    assertTrue(MinecraftVersionRequirement.Version.parse("").isEmpty());
  }
}
