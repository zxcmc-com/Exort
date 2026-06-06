package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PacketLocalizationLevelTest {
  @Test
  void parsesKnownLevelsCaseInsensitively() {
    assertEquals(PacketLocalizationLevel.SIMPLE, PacketLocalizationLevel.fromString("SIMPLE"));
    assertEquals(PacketLocalizationLevel.FULL, PacketLocalizationLevel.fromString("full"));
  }

  @Test
  void defaultsInvalidValuesToSimple() {
    assertEquals(PacketLocalizationLevel.SIMPLE, PacketLocalizationLevel.fromString(null));
    assertEquals(PacketLocalizationLevel.SIMPLE, PacketLocalizationLevel.fromString(""));
    assertEquals(PacketLocalizationLevel.SIMPLE, PacketLocalizationLevel.fromString("wide"));
  }
}
