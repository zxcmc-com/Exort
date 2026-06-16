package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sk89q.worldedit.math.BlockVector3;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class RelayLinkRewriteTest {
  @Test
  void copyPastePreservesRelayLinkOnlyWhenBothEndpointsAreInSameOperation() {
    UUID sourceWorld = uuid(1);
    UUID destinationWorld = uuid(2);
    BlockVector3 firstSource = BlockVector3.at(0, 64, 0);
    BlockVector3 secondSource = BlockVector3.at(8, 64, 0);
    BlockVector3 firstDestination = BlockVector3.at(100, 64, 0);
    BlockVector3 secondDestination = BlockVector3.at(108, 64, 0);

    MarkerSnapshot rewritten =
        RelayLinkRewrite.rewrite(
            snapshot(link(sourceWorld, secondSource)),
            firstSource,
            sourceWorld,
            destinationWorld,
            Map.of(firstSource, firstDestination, secondSource, secondDestination),
            Map.of(
                firstSource,
                snapshot(link(sourceWorld, secondSource)),
                secondSource,
                snapshot(link(sourceWorld, firstSource))));

    assertEquals(destinationWorld, rewritten.relay().link().worldId());
    assertEquals(secondDestination.x(), rewritten.relay().link().x());
    assertEquals(secondDestination.y(), rewritten.relay().link().y());
    assertEquals(secondDestination.z(), rewritten.relay().link().z());
  }

  @Test
  void singleEndpointCopyPasteClearsRelayLink() {
    UUID sourceWorld = uuid(3);
    UUID destinationWorld = uuid(4);
    BlockVector3 firstSource = BlockVector3.at(0, 64, 0);
    BlockVector3 secondSource = BlockVector3.at(8, 64, 0);
    BlockVector3 firstDestination = BlockVector3.at(100, 64, 0);

    MarkerSnapshot rewritten =
        RelayLinkRewrite.rewrite(
            snapshot(link(sourceWorld, secondSource)),
            firstSource,
            sourceWorld,
            destinationWorld,
            Map.of(firstSource, firstDestination),
            Map.of(firstSource, snapshot(link(sourceWorld, secondSource))));

    assertEquals(new RelayData(null), rewritten.relay());
  }

  @Test
  void movePreservesReciprocalLinksAtTransformedCoordinates() {
    UUID world = uuid(5);
    BlockVector3 firstSource = BlockVector3.at(0, 64, 0);
    BlockVector3 secondSource = BlockVector3.at(8, 64, 0);
    BlockVector3 firstDestination = BlockVector3.at(0, 65, 3);
    BlockVector3 secondDestination = BlockVector3.at(8, 65, 3);

    MarkerSnapshot rewritten =
        RelayLinkRewrite.rewrite(
            snapshot(link(world, secondSource)),
            firstSource,
            world,
            world,
            Map.of(firstSource, firstDestination, secondSource, secondDestination),
            Map.of(
                firstSource,
                snapshot(link(world, secondSource)),
                secondSource,
                snapshot(link(world, firstSource))));

    assertEquals(world, rewritten.relay().link().worldId());
    assertEquals(secondDestination.x(), rewritten.relay().link().x());
    assertEquals(secondDestination.y(), rewritten.relay().link().y());
    assertEquals(secondDestination.z(), rewritten.relay().link().z());
  }

  @Test
  void replacingExistingRelayThroughWorldEditUnlinksOldLoadedPeer() {
    Plugin plugin = BukkitTestDoubles.plugin();
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("we-relay-replace", uuid(6));
    Block replaced = world.block(0, 64, 0, Material.BARRIER);
    Block oldPeer = world.block(32, 64, 0, Material.BARRIER);
    RelayMarker.link(plugin, replaced, oldPeer);

    WorldEditBridge.unlinkExistingRelayForReplacement(plugin, replaced);

    assertFalse(RelayMarker.link(plugin, replaced).isPresent());
    assertFalse(RelayMarker.link(plugin, oldPeer).isPresent());
  }

  private static MarkerSnapshot snapshot(RelayLinkData link) {
    return new MarkerSnapshot(null, null, null, null, new RelayData(link), false, false);
  }

  private static RelayLinkData link(UUID world, BlockVector3 position) {
    return new RelayLinkData(world, position.x(), position.y(), position.z());
  }

  private static UUID uuid(int value) {
    return new UUID(0L, value);
  }
}
