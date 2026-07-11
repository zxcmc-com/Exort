package com.zxcmc.exort.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtectionStartupLogTest {
  @Test
  void failOpenOverrideIsExplicitlyUnsafeAndActionable() {
    String warning = ProtectionStartupLog.failOpenOverride();

    assertTrue(warning.contains("UNSAFE EXPERT OVERRIDE"));
    assertTrue(warning.contains("protection.failClosedOnError=false"));
    assertTrue(warning.contains("bypass"));
  }
}
