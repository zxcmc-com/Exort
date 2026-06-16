package com.zxcmc.exort.marker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class RelayMarkerTest {
  @Test
  void linkCreatesReciprocalOneToOnePair() {
    Plugin plugin = BukkitTestDoubles.plugin();
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-marker-link", uuid(1));
    Block first = world.block(0, 64, 0, Material.BARRIER);
    Block second = world.block(32, 64, 0, Material.BARRIER);

    RelayMarker.link(plugin, first, second);

    assertTrue(RelayMarker.link(plugin, first).filter(link -> link.sameBlock(second)).isPresent());
    assertTrue(RelayMarker.link(plugin, second).filter(link -> link.sameBlock(first)).isPresent());
  }

  @Test
  void unlinkLoadedPairClearsBothLoadedEndpoints() {
    Plugin plugin = BukkitTestDoubles.plugin();
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-marker-unlink", uuid(2));
    Block first = world.block(0, 64, 0, Material.BARRIER);
    Block second = world.block(32, 64, 0, Material.BARRIER);
    RelayMarker.link(plugin, first, second);

    RelayMarker.unlinkLoadedPair(plugin, first);

    assertFalse(RelayMarker.link(plugin, first).isPresent());
    assertFalse(RelayMarker.link(plugin, second).isPresent());
  }

  @Test
  void unlinkLoadedPairLeavesUnloadedPeerMarkerUntouched() {
    Plugin plugin = BukkitTestDoubles.plugin();
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-marker-unloaded", uuid(3));
    Block first = world.block(0, 64, 0, Material.BARRIER);
    Block second = world.block(32, 64, 0, Material.BARRIER);
    RelayMarker.link(plugin, first, second);
    world.unloadChunk(2, 0);

    RelayMarker.unlinkLoadedPair(plugin, first);

    assertFalse(RelayMarker.link(plugin, first).isPresent());
    assertTrue(RelayMarker.link(plugin, second).filter(link -> link.sameBlock(first)).isPresent());
  }

  @Test
  void unlinkLoadedPairDoesNotClearNonReciprocalLoadedPeer() {
    Plugin plugin = BukkitTestDoubles.plugin();
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-marker-stale", uuid(4));
    Block first = world.block(0, 64, 0, Material.BARRIER);
    Block second = world.block(32, 64, 0, Material.BARRIER);
    Block third = world.block(48, 64, 0, Material.BARRIER);
    RelayMarker.setLink(plugin, first, RelayMarker.Link.of(second));
    RelayMarker.setLink(plugin, second, RelayMarker.Link.of(third));

    RelayMarker.unlinkLoadedPair(plugin, first);

    assertFalse(RelayMarker.link(plugin, first).isPresent());
    assertTrue(RelayMarker.link(plugin, second).filter(link -> link.sameBlock(third)).isPresent());
  }

  private static java.util.UUID uuid(int value) {
    return new java.util.UUID(0L, value);
  }
}
