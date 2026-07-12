package com.zxcmc.exort.wireless.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class WirelessListenerChunkTest {
  @Test
  void checksAnchorChunkWithoutAccessingOrLoadingItsBlock() {
    BukkitTestDoubles.TestWorld world =
        BukkitTestDoubles.world("wireless-anchor", UUID.randomUUID());
    Location anchor = new Location(world.world(), -1, 64, -17);

    world.unloadChunk(-1, -2);
    assertFalse(WirelessListener.isAnchorChunkLoaded(anchor));
    assertEquals(0, world.getBlockAtCalls());

    world.loadChunk(-1, -2);
    assertTrue(WirelessListener.isAnchorChunkLoaded(anchor));
    assertEquals(0, world.getBlockAtCalls());
  }
}
