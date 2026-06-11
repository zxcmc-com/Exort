package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PacketEnhancementsFactoryTest {
  @Test
  void missingPacketEventsFallbackMessageNamesPaperFallbacks() {
    assertEquals(
        "[PacketEvents] Plugin not found; using Paper fallbacks for optional packet features.",
        PacketEnhancementsFactory.unavailableFallbackMessage(true, null));
  }

  @Test
  void disabledPacketEventsFallbackMessageIncludesVersionWhenKnown() {
    assertEquals(
        "[PacketEvents] Plugin is installed (2.12.2) but not enabled; using Paper fallbacks for"
            + " optional packet features.",
        PacketEnhancementsFactory.unavailableFallbackMessage(false, "2.12.2"));
  }
}
