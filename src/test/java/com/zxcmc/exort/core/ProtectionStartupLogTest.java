package com.zxcmc.exort.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtectionStartupLogTest {
  @Test
  void startupMessagesDoNotUseLegacyMissingAdapterFormat() {
    assertDoesNotUseLegacyMissingAdapterFormat(ProtectionStartupLog.disabledByConfig());
    assertDoesNotUseLegacyMissingAdapterFormat(ProtectionStartupLog.noSupportedProvider());
    assertDoesNotUseLegacyMissingAdapterFormat(ProtectionStartupLog.enabled(List.of("WorldGuard")));
  }

  @Test
  void noSupportedProviderMessageExplainsAllowAllMode() {
    assertEquals(
        "[Protection] No supported protection plugin found; using allow-all mode.",
        ProtectionStartupLog.noSupportedProvider());
  }

  @Test
  void enabledMessageKeepsExistingProviderSummary() {
    assertEquals(
        "[Protection] Integration enabled: WorldGuard",
        ProtectionStartupLog.enabled(List.of("WorldGuard")));
  }

  @Test
  void disabledByConfigMessageIsUnchanged() {
    assertEquals(
        "[Protection] Integration disabled by config.", ProtectionStartupLog.disabledByConfig());
  }

  private static void assertDoesNotUseLegacyMissingAdapterFormat(String message) {
    assertFalse(message.contains("adapter disabled"));
  }
}
