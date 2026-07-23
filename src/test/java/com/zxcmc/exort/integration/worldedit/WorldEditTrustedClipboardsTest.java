package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.util.Map;
import java.util.UUID;
import org.enginehub.linbus.tree.LinCompoundTag;
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

  @Test
  void trustedSchematicPatchRebasesOnlyAcrossEqualSizedClipboardRegions() {
    BlockVector3 originalMinimum = BlockVector3.at(100, 40, -20);
    BlockVector3 originalMaximum = BlockVector3.at(102, 41, -18);
    BlockVector3 markerPosition = BlockVector3.at(101, 40, -19);
    LinCompoundTag marker = LinCompoundTag.builder().putByte("wire", (byte) 1).build();
    PendingClipboardPatch patch =
        new PendingClipboardPatch(
            UUID.randomUUID(),
            new WorldEditBounds(originalMinimum, originalMaximum),
            originalMinimum,
            Map.of(markerPosition, marker));
    BlockArrayClipboard loaded =
        clipboard(BlockVector3.at(0, 0, 0), BlockVector3.at(2, 1, 2), BlockVector3.ZERO);

    PendingClipboardPatch rebased = patch.rebaseTo(loaded);

    assertEquals(Map.of(BlockVector3.at(1, 0, 1), marker), rebased.markers());
    assertTrue(rebased.matches(loaded));
    assertNull(
        patch.rebaseTo(
            clipboard(BlockVector3.at(0, 0, 0), BlockVector3.at(3, 1, 2), BlockVector3.ZERO)));
  }

  private static BlockArrayClipboard clipboard(
      BlockVector3 minimum, BlockVector3 maximum, BlockVector3 origin) {
    BlockArrayClipboard clipboard = new BlockArrayClipboard(new CuboidRegion(minimum, maximum));
    clipboard.setOrigin(origin);
    return clipboard;
  }
}
