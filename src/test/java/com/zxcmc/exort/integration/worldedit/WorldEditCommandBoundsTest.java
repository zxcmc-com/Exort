package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

class WorldEditCommandBoundsTest {
  private static final WorldEditBounds SELECTION =
      new WorldEditBounds(BlockVector3.at(10, 20, 30), BlockVector3.at(12, 23, 34));
  private static final BlockVector3 PLACEMENT = BlockVector3.at(100, 64, -100);

  @Test
  void selectionCommandsKeepExactSelectionBounds() {
    assertEquals(
        SELECTION,
        WorldEditCommandParser.affectedBounds(
            "//set stone", SELECTION, PLACEMENT, BlockVector3.at(1, 0, 0)));
  }

  @Test
  void radiusAndDepthCommandsAreBoundedAroundPlacement() {
    assertEquals(
        WorldEditBounds.around(PLACEMENT, 8, 8),
        WorldEditCommandParser.affectedBounds(
            "//replacenear 8 stone air", SELECTION, PLACEMENT, null));
    assertEquals(
        WorldEditBounds.around(PLACEMENT, 12, 4),
        WorldEditCommandParser.affectedBounds("//fill water 12 4", SELECTION, PLACEMENT, null));
    assertEquals(
        WorldEditBounds.around(PLACEMENT, 6, 6),
        WorldEditCommandParser.affectedBounds("//drain 6 -w", SELECTION, PLACEMENT, null));
  }

  @Test
  void stackUnionsSelectionWithCalculatedDestination() {
    WorldEditBounds expected =
        SELECTION.union(SELECTION.translate(BlockVector3.at(SELECTION.sizeX() * 3, 0, 0)));

    assertEquals(
        expected,
        WorldEditCommandParser.affectedBounds(
            "//stack 3 east", SELECTION, PLACEMENT, BlockVector3.at(1, 0, 0)));
  }

  @Test
  void malformedAndOverflowArgumentsDoNotFallBackToWorldWideCapture() {
    assertNull(
        WorldEditCommandParser.affectedBounds(
            "//replacenear nope stone air", SELECTION, PLACEMENT, null));
    assertNull(
        WorldEditCommandParser.affectedBounds(
            "//stack 999999999999 east", SELECTION, PLACEMENT, BlockVector3.at(1, 0, 0)));
  }

  @Test
  void chunkCountIsExactUntilTheCallerProvidedCap() {
    WorldEditBounds bounds =
        new WorldEditBounds(BlockVector3.at(-16, 0, -1), BlockVector3.at(31, 10, 32));

    assertEquals(12, bounds.chunkCountCapped(100));
    assertEquals(10, bounds.chunkCountCapped(10));
  }

  @Test
  void onlyCopyClearAndSchematicLoadInvalidateClipboardTrust() {
    assertTrue(WorldEditCommandParser.invalidatesClipboardTrust("//copy"));
    assertTrue(WorldEditCommandParser.invalidatesClipboardTrust("//lazycut"));
    assertTrue(WorldEditCommandParser.invalidatesClipboardTrust("//clearclipboard"));
    assertTrue(WorldEditCommandParser.invalidatesClipboardTrust("//schem load example"));
    assertFalse(WorldEditCommandParser.invalidatesClipboardTrust("//schem save example"));
    assertFalse(WorldEditCommandParser.invalidatesClipboardTrust("//rotate 90"));
    assertFalse(WorldEditCommandParser.invalidatesClipboardTrust("//flip north"));
    assertFalse(WorldEditCommandParser.invalidatesClipboardTrust("//paste"));
  }

  @Test
  void schematicCommandsResolveTheFinalNonFlagName() {
    assertEquals(
        "folder/example",
        WorldEditCommandParser.schematicName(
            "//schem save -f sponge folder/Example.schem", "save"));
    assertEquals(
        "folder/example",
        WorldEditCommandParser.schematicName(
            "/worldedit:schematic load folder/EXAMPLE.schematic", "load"));
    assertNull(WorldEditCommandParser.schematicName("//schem list", "load"));
    assertNull(WorldEditCommandParser.schematicName("//schem load", "load"));
  }
}
