package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldEditTrustedClipboardsTest {
  @Test
  void trustRequiresSameIdentityRegionAndOriginButSurvivesRepeatedChecks() {
    UUID actorId = new UUID(1L, 2L);
    BlockVector3 min = BlockVector3.at(1, 2, 3);
    BlockVector3 max = BlockVector3.at(4, 5, 6);
    BlockVector3 origin = BlockVector3.at(2, 2, 3);
    BlockArrayClipboard clipboard = clipboard(min, max, origin);
    PendingClipboardPatch patch =
        new PendingClipboardPatch(
            new UUID(3L, 4L), new WorldEditBounds(min, max), origin, Map.of());
    WorldEditTrustedClipboards trusted = new WorldEditTrustedClipboards();

    trusted.trust(actorId, clipboard, patch);

    assertTrue(trusted.matches(actorId, clipboard));
    assertTrue(trusted.matches(actorId, clipboard));
    assertFalse(trusted.matches(actorId, clipboard(min, max, origin)));
  }

  @Test
  void nextCopyClearAndReplacementInvalidateOldClipboard() {
    UUID actorId = new UUID(5L, 6L);
    BlockVector3 min = BlockVector3.at(0, 0, 0);
    BlockVector3 max = BlockVector3.at(1, 1, 1);
    BlockVector3 origin = BlockVector3.at(0, 0, 0);
    BlockArrayClipboard first = clipboard(min, max, origin);
    BlockArrayClipboard second = clipboard(min, max, origin);
    PendingClipboardPatch patch =
        new PendingClipboardPatch(
            new UUID(7L, 8L), new WorldEditBounds(min, max), origin, Map.of());
    WorldEditTrustedClipboards trusted = new WorldEditTrustedClipboards();

    trusted.trust(actorId, first, patch);
    trusted.trust(actorId, second, patch);
    assertFalse(trusted.matches(actorId, first));
    assertTrue(trusted.matches(actorId, second));

    trusted.clear(actorId);
    assertFalse(trusted.matches(actorId, second));
  }

  @Test
  void changedRegionOrOriginInvalidatesTheTrustedIdentity() {
    UUID actorId = new UUID(9L, 10L);
    BlockVector3 min = BlockVector3.at(0, 0, 0);
    BlockVector3 max = BlockVector3.at(1, 1, 1);
    BlockVector3 origin = BlockVector3.at(0, 0, 0);
    BlockArrayClipboard clipboard = clipboard(min, max, origin);
    PendingClipboardPatch patch =
        new PendingClipboardPatch(
            new UUID(11L, 12L), new WorldEditBounds(min, max), origin, Map.of());
    WorldEditTrustedClipboards trusted = new WorldEditTrustedClipboards();
    trusted.trust(actorId, clipboard, patch);

    clipboard.setOrigin(BlockVector3.at(1, 0, 0));

    assertFalse(trusted.matches(actorId, clipboard));
  }

  private static BlockArrayClipboard clipboard(
      BlockVector3 minimum, BlockVector3 maximum, BlockVector3 origin) {
    BlockArrayClipboard clipboard = new BlockArrayClipboard(new CuboidRegion(minimum, maximum));
    clipboard.setOrigin(origin);
    return clipboard;
  }
}
