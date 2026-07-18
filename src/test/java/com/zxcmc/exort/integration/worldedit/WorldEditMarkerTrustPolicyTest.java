package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.enginehub.linbus.tree.LinCompoundTag;
import org.junit.jupiter.api.Test;

class WorldEditMarkerTrustPolicyTest {
  private static final MarkerSnapshot WIRE =
      new MarkerSnapshot(null, null, null, null, null, false, true);

  @Test
  void externalAndMalformedMarkerTagsAreRejectedWithoutBlockingVanillaBlocks() {
    LinCompoundTag tag = LinCompoundTag.builder().putByte("wire", (byte) 1).build();

    assertTrue(WorldEditMarkerTrustPolicy.rejectIncoming(tag, WIRE, false));
    assertTrue(WorldEditMarkerTrustPolicy.rejectIncoming(tag, null, true));
    assertFalse(WorldEditMarkerTrustPolicy.rejectIncoming(null, null, false));
    assertFalse(WorldEditMarkerTrustPolicy.rejectIncoming(tag, WIRE, true));
  }

  @Test
  void directApiCannotReplaceManagedBlockWithoutPreparedContext() {
    assertTrue(WorldEditMarkerTrustPolicy.rejectExisting(WIRE, false));
    assertFalse(WorldEditMarkerTrustPolicy.rejectExisting(WIRE, true));
    assertFalse(WorldEditMarkerTrustPolicy.rejectExisting(null, false));
  }
}
