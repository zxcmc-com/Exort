package com.zxcmc.exort.marker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class BridgeMarkerTest {
  @Test
  void linkCreatesReciprocalOneToOnePair() {
    Plugin plugin = BukkitTestDoubles.plugin();
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("bridge-marker-link", uuid(1));
    Block first = world.block(0, 64, 0, Material.BARRIER);
    Block second = world.block(32, 64, 0, Material.BARRIER);

    BridgeMarker.link(plugin, first, second);

    assertTrue(BridgeMarker.link(plugin, first).filter(link -> link.sameBlock(second)).isPresent());
    assertTrue(BridgeMarker.link(plugin, second).filter(link -> link.sameBlock(first)).isPresent());
  }

  @Test
  void unlinkLoadedPairClearsBothLoadedEndpoints() {
    Plugin plugin = BukkitTestDoubles.plugin();
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("bridge-marker-unlink", uuid(2));
    Block first = world.block(0, 64, 0, Material.BARRIER);
    Block second = world.block(32, 64, 0, Material.BARRIER);
    BridgeMarker.link(plugin, first, second);

    BridgeMarker.unlinkLoadedPair(plugin, first);

    assertFalse(BridgeMarker.link(plugin, first).isPresent());
    assertFalse(BridgeMarker.link(plugin, second).isPresent());
  }

  @Test
  void unlinkLoadedPairLeavesUnloadedPeerMarkerUntouched() {
    Plugin plugin = BukkitTestDoubles.plugin();
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("bridge-marker-unloaded", uuid(3));
    Block first = world.block(0, 64, 0, Material.BARRIER);
    Block second = world.block(32, 64, 0, Material.BARRIER);
    BridgeMarker.link(plugin, first, second);
    world.unloadChunk(2, 0);

    BridgeMarker.unlinkLoadedPair(plugin, first);

    assertFalse(BridgeMarker.link(plugin, first).isPresent());
    assertTrue(BridgeMarker.link(plugin, second).filter(link -> link.sameBlock(first)).isPresent());
  }

  @Test
  void unlinkLoadedPairDoesNotClearNonReciprocalLoadedPeer() {
    Plugin plugin = BukkitTestDoubles.plugin();
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("bridge-marker-stale", uuid(4));
    Block first = world.block(0, 64, 0, Material.BARRIER);
    Block second = world.block(32, 64, 0, Material.BARRIER);
    Block third = world.block(48, 64, 0, Material.BARRIER);
    BridgeMarker.setLink(plugin, first, BridgeMarker.Link.of(second));
    BridgeMarker.setLink(plugin, second, BridgeMarker.Link.of(third));

    BridgeMarker.unlinkLoadedPair(plugin, first);

    assertFalse(BridgeMarker.link(plugin, first).isPresent());
    assertTrue(BridgeMarker.link(plugin, second).filter(link -> link.sameBlock(third)).isPresent());
  }

  private static java.util.UUID uuid(int value) {
    return new java.util.UUID(0L, value);
  }
}
