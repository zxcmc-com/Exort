package com.zxcmc.exort.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class RelaySetupTrackerTest {
  @Test
  void selectAndReplaceTracksLatestBlock() {
    RelaySetupTracker tracker = new RelaySetupTracker(1_000L);
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-setup-replace", uuid(1));
    UUID player = uuid(100);
    Block first = world.block(0, 64, 0, Material.BARRIER);
    Block second = world.block(1, 64, 0, Material.BARRIER);

    assertNull(tracker.select(player, first, 10L));
    assertTrue(tracker.isPending(first, 20L));

    assertSame(first, tracker.select(player, second, 30L));

    assertFalse(tracker.isPending(first, 40L));
    assertTrue(tracker.isPending(second, 40L));
    assertEquals(1, tracker.pendingCount());
  }

  @Test
  void clearPlayerRemovesPendingBlock() {
    RelaySetupTracker tracker = new RelaySetupTracker(1_000L);
    BukkitTestDoubles.TestWorld world =
        BukkitTestDoubles.world("relay-setup-clear-player", uuid(2));
    UUID player = uuid(101);
    Block relay = world.block(0, 64, 0, Material.BARRIER);
    tracker.select(player, relay, 10L);

    assertSame(relay, tracker.clearPlayer(player));

    assertFalse(tracker.isPending(relay, 20L));
    assertEquals(0, tracker.pendingCount());
  }

  @Test
  void clearBlockRemovesAllPlayersOnThatBlockOnly() {
    RelaySetupTracker tracker = new RelaySetupTracker(1_000L);
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-setup-clear-block", uuid(3));
    Block relay = world.block(0, 64, 0, Material.BARRIER);
    Block other = world.block(1, 64, 0, Material.BARRIER);
    tracker.select(uuid(201), relay, 10L);
    tracker.select(uuid(202), relay, 20L);
    tracker.select(uuid(203), other, 30L);

    assertTrue(tracker.clearBlock(relay));

    assertFalse(tracker.isPending(relay, 40L));
    assertTrue(tracker.isPending(other, 40L));
    assertEquals(1, tracker.pendingCount());
  }

  @Test
  void ttlExpiryRemovesPendingSelection() {
    RelaySetupTracker tracker = new RelaySetupTracker(100L);
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-setup-ttl", uuid(4));
    Block relay = world.block(0, 64, 0, Material.BARRIER);
    tracker.select(uuid(301), relay, 1_000L);

    assertTrue(tracker.isPending(relay, 1_099L));
    assertFalse(tracker.isPending(relay, 1_100L));
    assertEquals(0, tracker.pendingCount());
  }

  @Test
  void multiplePlayersCanKeepSameBlockPendingUntilAllClear() {
    RelaySetupTracker tracker = new RelaySetupTracker(1_000L);
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-setup-multiple", uuid(5));
    Block relay = world.block(0, 64, 0, Material.BARRIER);
    UUID first = uuid(401);
    UUID second = uuid(402);
    tracker.select(first, relay, 10L);
    tracker.select(second, relay, 20L);

    assertSame(relay, tracker.clearPlayer(first));
    assertTrue(tracker.isPending(relay, 30L));

    assertSame(relay, tracker.clearPlayer(second));
    assertFalse(tracker.isPending(relay, 40L));
  }

  private static UUID uuid(int value) {
    return new UUID(0L, value);
  }
}
