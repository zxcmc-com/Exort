package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProtocolLocalizationLevelTest {
  @Test
  void parsesKnownValuesCaseInsensitively() {
    assertEquals(ProtocolLocalizationLevel.SIMPLE, ProtocolLocalizationLevel.fromString("SIMPLE"));
    assertEquals(ProtocolLocalizationLevel.FULL, ProtocolLocalizationLevel.fromString("full"));
  }

  @Test
  void invalidValuesFallBackToSimple() {
    assertEquals(ProtocolLocalizationLevel.SIMPLE, ProtocolLocalizationLevel.fromString(null));
    assertEquals(ProtocolLocalizationLevel.SIMPLE, ProtocolLocalizationLevel.fromString(""));
    assertEquals(ProtocolLocalizationLevel.SIMPLE, ProtocolLocalizationLevel.fromString("wide"));
  }
}
