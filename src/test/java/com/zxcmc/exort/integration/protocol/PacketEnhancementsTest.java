package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PacketEnhancementsTest {
  @Test
  void displayCullingViewRangeMetadataIndexStaysNamed() {
    assertEquals(17, PacketEnhancements.DisplayCullingPackets.VIEW_RANGE_METADATA_INDEX);
  }
}
