package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.math.BlockVector3;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinStringTag;
import org.enginehub.linbus.tree.LinTagType;
import org.junit.jupiter.api.Test;

class WorldEditHistoryCarrierTest {
  @Test
  void historyCarrierRootPreservesExortNbtForRedoReplay() {
    LinCompoundTag terminalTag =
        LinCompoundTag.builder().putString("type", "TERMINAL").putString("facing", "NORTH").build();
    LinCompoundTag exort = LinCompoundTag.builder().put("terminal", terminalTag).build();

    LinCompoundTag root = WorldEditBridge.markerHistoryCarrierRoot(Material.BARRIER, exort);

    assertNotNull(root);
    assertNotNull(root.findTag("exort", LinTagType.compoundTag()));
    LinStringTag id = root.findTag("id", LinTagType.stringTag());
    assertNotNull(id);
    assertEquals("minecraft:barrier", id.value());
  }

  @Test
  void historyReplayDoesNotClearRelayLinksFromNbt() {
    MarkerSnapshot relaySnapshot =
        new MarkerSnapshot(
            null,
            null,
            null,
            null,
            new RelayData(new RelayLinkData(new UUID(0L, 1L), 8, 64, 8)),
            false,
            false);

    assertTrue(
        WorldEditBridge.shouldClearRelayLinkFromClipboard(
            relaySnapshot, true, false, false, null, EditSession.Stage.BEFORE_HISTORY));
    assertFalse(
        WorldEditBridge.shouldClearRelayLinkFromClipboard(
            relaySnapshot,
            true,
            false,
            false,
            HistoryAction.REDO,
            EditSession.Stage.BEFORE_REORDER));
    assertFalse(
        WorldEditBridge.shouldClearRelayLinkFromClipboard(
            relaySnapshot, true, false, false, null, EditSession.Stage.BEFORE_CHANGE));
    assertFalse(
        WorldEditBridge.shouldClearRelayLinkFromClipboard(
            relaySnapshot, true, true, false, null, EditSession.Stage.BEFORE_HISTORY));
  }

  @Test
  void normalMarkerPlacementSeedsRedoFallback() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(1L, 2L);
    UUID worldId = new UUID(3L, 4L);
    MarkerSnapshot terminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "NORTH"), null, null, null, false, false);

    history.rememberRedoTarget(actorId, worldId, 4, 80, 12, terminal);

    assertEquals(terminal, history.peek(actorId, HistoryAction.REDO, worldId, 4, 80, 12));
    assertEquals(terminal, history.consume(actorId, HistoryAction.REDO, worldId, 4, 80, 12));
    assertNull(history.peek(actorId, HistoryAction.REDO, worldId, 4, 80, 12));
  }

  @Test
  void storageHistoryKeepsClonePolicyWithRedoTargetOnly() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(71L, 72L);
    UUID worldId = new UUID(73L, 74L);
    MarkerSnapshot storage =
        new MarkerSnapshot(
            new StorageData("storage-history", "BASIC", "NORTH"),
            null,
            null,
            null,
            null,
            false,
            false);

    WorldEditMarkerHistory.Frame frame = history.beginNormalOperation(actorId, worldId, 1L);
    history.remember(actorId, null, worldId, 5, 80, 6, storage, frame);
    history.rememberRedoTarget(actorId, worldId, 5, 80, 7, storage, frame, true);

    assertFalse(history.peekState(frame, HistoryAction.UNDO, 5, 80, 6).storageCloneRequired());
    assertTrue(history.peekState(frame, HistoryAction.REDO, 5, 80, 7).storageCloneRequired());
    assertTrue(
        history.peekState(actorId, HistoryAction.REDO, worldId, 5, 80, 7).storageCloneRequired());
  }

  @Test
  void perBlockFallbackKeepsMarkerWhenReplayFrameMissesCoordinate() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(51L, 52L);
    UUID worldId = new UUID(53L, 54L);
    MarkerSnapshot terminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "NORTH"), null, null, null, false, false);
    MarkerSnapshot storage =
        new MarkerSnapshot(
            new StorageData("storage-miss", "BASIC", "SOUTH"),
            null,
            null,
            null,
            null,
            false,
            false);

    WorldEditMarkerHistory.Frame frameWithTerminal =
        history.beginNormalOperation(actorId, worldId, 1L);
    history.remember(actorId, null, worldId, 10, 70, -10, terminal, frameWithTerminal);
    WorldEditMarkerHistory.Frame unrelatedFrame =
        history.beginNormalOperation(actorId, worldId, 2L);
    history.remember(actorId, null, worldId, 20, 70, -20, storage, unrelatedFrame);

    WorldEditMarkerHistory.Frame replayFrame =
        history.beginReplay(actorId, worldId, HistoryAction.UNDO);

    assertNull(history.peekState(replayFrame, HistoryAction.UNDO, 10, 70, -10));
    assertEquals(
        terminal, history.peekState(actorId, HistoryAction.UNDO, worldId, 10, 70, -10).snapshot());
    assertEquals(
        terminal,
        history.consumeState(actorId, HistoryAction.UNDO, worldId, 10, 70, -10).snapshot());
    assertNull(history.peekState(actorId, HistoryAction.UNDO, worldId, 10, 70, -10));
  }

  @Test
  void perBlockFallbackKeepsClearStateWhenReplayFrameMissesCoordinate() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(61L, 62L);
    UUID worldId = new UUID(63L, 64L);
    MarkerSnapshot terminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "NORTH"), null, null, null, false, false);

    WorldEditMarkerHistory.Frame frameWithClear =
        history.beginNormalOperation(actorId, worldId, 1L);
    history.rememberUndoClear(actorId, worldId, frameWithClear, 10, 70, -10);
    WorldEditMarkerHistory.Frame unrelatedFrame =
        history.beginNormalOperation(actorId, worldId, 2L);
    history.remember(actorId, null, worldId, 20, 70, -20, terminal, unrelatedFrame);

    WorldEditMarkerHistory.Frame replayFrame =
        history.beginReplay(actorId, worldId, HistoryAction.UNDO);

    assertNull(history.peekState(replayFrame, HistoryAction.UNDO, 10, 70, -10));
    assertTrue(history.peekState(actorId, HistoryAction.UNDO, worldId, 10, 70, -10).clear());
    assertTrue(history.consumeState(actorId, HistoryAction.UNDO, worldId, 10, 70, -10).clear());
    assertNull(history.peekState(actorId, HistoryAction.UNDO, worldId, 10, 70, -10));
  }

  @Test
  void moveSourceClearSeedsUndoFallbackFromMoveSidecar() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(21L, 22L);
    UUID worldId = new UUID(23L, 24L);
    BlockVector3 source = BlockVector3.at(10, 64, -30);
    BlockVector3 offset = BlockVector3.at(0, 0, 5);
    BlockVector3 destination = source.add(offset);
    MarkerSnapshot terminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "NORTH"), null, null, null, false, false);
    PendingMovePatch movePatch =
        new PendingMovePatch(
            Map.of(WorldEditMarkerMath.blockKey(source.x(), source.y(), source.z()), terminal),
            Map.of(
                WorldEditMarkerMath.blockKey(destination.x(), destination.y(), destination.z()),
                terminal),
            offset,
            System.currentTimeMillis(),
            3);

    WorldEditMarkerHistory.Frame frame = history.beginNormalOperation(actorId, worldId, 1L);
    MarkerSnapshot seeded =
        WorldEditBridge.rememberMoveSourceHistory(
            history, actorId, worldId, source, movePatch, frame);

    assertEquals(terminal, seeded);
    assertEquals(terminal, history.peek(frame, HistoryAction.UNDO, 10, 64, -30));
    assertEquals(terminal, history.consume(actorId, HistoryAction.UNDO, worldId, 10, 64, -30));
    assertNull(history.consume(actorId, HistoryAction.UNDO, worldId, 10, 64, -25));
  }

  @Test
  void cutSourceClearSeedsUndoFallbackBeforeEarlyReturn() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(25L, 26L);
    UUID worldId = new UUID(27L, 28L);
    BlockVector3 source = BlockVector3.at(280, 84, 80);
    MarkerSnapshot terminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "NORTH"), null, null, null, false, false);
    HashSet<Long> remembered = new HashSet<>();

    WorldEditMarkerHistory.Frame frame = history.beginNormalOperation(actorId, worldId, 1L);

    assertTrue(
        WorldEditBridge.rememberHistorySnapshotOnce(
            history, remembered, actorId, null, worldId, source, terminal, frame));
    assertFalse(
        WorldEditBridge.rememberHistorySnapshotOnce(
            history, remembered, actorId, null, worldId, source, terminal, frame));
    assertEquals(terminal, history.peek(frame, HistoryAction.UNDO, 280, 84, 80));
    assertEquals(terminal, history.consume(actorId, HistoryAction.UNDO, worldId, 280, 84, 80));
    assertNull(history.consume(actorId, HistoryAction.UNDO, worldId, 280, 84, 80));
  }

  @Test
  void pasteUndoSnapshotSeedsHistoryWhenChunkSnapshotMissesExistingMarker() {
    MarkerSnapshot previousTerminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "NORTH"), null, null, null, false, false);
    MarkerSnapshot pastedTerminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "SOUTH"), null, null, null, false, false);
    MarkerSnapshot storage =
        new MarkerSnapshot(
            new StorageData("storage-existing", "BASIC", "WEST"),
            null,
            null,
            null,
            null,
            false,
            false);
    BlockVector3 position = BlockVector3.at(40, 84, 160);
    PendingPastePatch pastePatch =
        new PendingPastePatch(
            Map.of(WorldEditMarkerMath.blockKey(40, 84, 160), pastedTerminal),
            Map.of(WorldEditMarkerMath.blockKey(40, 84, 160), previousTerminal));

    assertEquals(pastedTerminal, pastePatch.get(position));
    assertEquals(previousTerminal, pastePatch.undo(position));
    assertEquals(
        previousTerminal,
        WorldEditBridge.chooseUndoSnapshot(null, null, pastePatch.undo(position), null));
    assertEquals(
        storage,
        WorldEditBridge.chooseUndoSnapshot(storage, null, pastePatch.undo(position), null));
  }

  @Test
  void operationFramesKeepOverlappingMoveHistoryInWorldEditOrder() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(31L, 32L);
    UUID worldId = new UUID(33L, 34L);
    MarkerSnapshot terminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "NORTH"), null, null, null, false, false);

    rememberMoveFrame(history, actorId, worldId, 1L, 628, 70, -104, 628, 70, -94, terminal);
    rememberMoveFrame(history, actorId, worldId, 2L, 628, 70, -94, 628, 75, -94, terminal);
    rememberMoveFrame(history, actorId, worldId, 3L, 628, 75, -94, 628, 75, -89, terminal);
    rememberMoveFrame(history, actorId, worldId, 4L, 628, 75, -89, 628, 75, -99, terminal);

    assertEquals(
        terminal,
        history.peek(
            history.beginReplay(actorId, worldId, HistoryAction.UNDO),
            HistoryAction.UNDO,
            628,
            75,
            -89));
    assertEquals(
        terminal,
        history.peek(
            history.beginReplay(actorId, worldId, HistoryAction.UNDO),
            HistoryAction.UNDO,
            628,
            75,
            -94));
    assertEquals(
        terminal,
        history.peek(
            history.beginReplay(actorId, worldId, HistoryAction.UNDO),
            HistoryAction.UNDO,
            628,
            70,
            -94));

    assertEquals(
        terminal,
        history.peek(
            history.beginReplay(actorId, worldId, HistoryAction.REDO),
            HistoryAction.REDO,
            628,
            75,
            -94));
    assertEquals(
        terminal,
        history.peek(
            history.beginReplay(actorId, worldId, HistoryAction.REDO),
            HistoryAction.REDO,
            628,
            75,
            -89));
    assertEquals(
        terminal,
        history.peek(
            history.beginReplay(actorId, worldId, HistoryAction.REDO),
            HistoryAction.REDO,
            628,
            75,
            -99));

    assertEquals(
        terminal,
        history.peek(
            history.beginReplay(actorId, worldId, HistoryAction.UNDO),
            HistoryAction.UNDO,
            628,
            75,
            -89));
    assertEquals(
        terminal,
        history.peek(
            history.beginReplay(actorId, worldId, HistoryAction.UNDO),
            HistoryAction.UNDO,
            628,
            75,
            -94));
  }

  @Test
  void operationFrameKeepsClearStateForSelfOverlappingMoveReplay() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(41L, 42L);
    UUID worldId = new UUID(43L, 44L);
    MarkerSnapshot terminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "NORTH"), null, null, null, false, false);
    MarkerSnapshot wire = new MarkerSnapshot(null, null, null, null, null, true, false);
    MarkerSnapshot relay =
        new MarkerSnapshot(null, null, null, null, new RelayData(null), false, false);

    WorldEditMarkerHistory.Frame frame = history.beginNormalOperation(actorId, worldId, 1L);
    history.remember(actorId, null, worldId, 627, 85, -92, terminal, frame);
    history.remember(actorId, null, worldId, 627, 85, -91, wire, frame);
    history.remember(actorId, null, worldId, 627, 85, -90, relay, frame);

    history.rememberRedoClear(frame, 627, 85, -92);
    history.rememberRedoClear(frame, 627, 85, -91);
    history.rememberRedoClear(frame, 627, 85, -90);
    history.rememberRedoTarget(actorId, worldId, 627, 85, -90, terminal, frame);
    history.rememberRedoTarget(actorId, worldId, 627, 85, -89, wire, frame);
    history.rememberUndoClear(frame, 627, 85, -89);
    history.rememberRedoTarget(actorId, worldId, 627, 85, -88, relay, frame);
    history.rememberUndoClear(frame, 627, 85, -88);

    WorldEditMarkerHistory.Frame undoFrame =
        history.beginReplay(actorId, worldId, HistoryAction.UNDO);
    assertEquals(terminal, history.peek(undoFrame, HistoryAction.UNDO, 627, 85, -92));
    assertEquals(wire, history.peek(undoFrame, HistoryAction.UNDO, 627, 85, -91));
    assertEquals(relay, history.peek(undoFrame, HistoryAction.UNDO, 627, 85, -90));
    assertTrue(history.peekState(undoFrame, HistoryAction.UNDO, 627, 85, -89).clear());
    assertTrue(history.peekState(undoFrame, HistoryAction.UNDO, 627, 85, -88).clear());

    WorldEditMarkerHistory.Frame redoFrame =
        history.beginReplay(actorId, worldId, HistoryAction.REDO);
    assertTrue(history.peekState(redoFrame, HistoryAction.REDO, 627, 85, -92).clear());
    assertTrue(history.peekState(redoFrame, HistoryAction.REDO, 627, 85, -91).clear());
    assertEquals(terminal, history.peek(redoFrame, HistoryAction.REDO, 627, 85, -90));
    assertEquals(wire, history.peek(redoFrame, HistoryAction.REDO, 627, 85, -89));
    assertEquals(relay, history.peek(redoFrame, HistoryAction.REDO, 627, 85, -88));
  }

  private static void rememberMoveFrame(
      WorldEditMarkerHistory history,
      UUID actorId,
      UUID worldId,
      long operationId,
      int sourceX,
      int sourceY,
      int sourceZ,
      int destinationX,
      int destinationY,
      int destinationZ,
      MarkerSnapshot snapshot) {
    WorldEditMarkerHistory.Frame frame =
        history.beginNormalOperation(actorId, worldId, operationId);
    history.remember(actorId, null, worldId, sourceX, sourceY, sourceZ, snapshot, frame);
    history.rememberRedoTarget(
        actorId, worldId, destinationX, destinationY, destinationZ, snapshot, frame);
  }

  @Test
  void historyParserReadsMultiStepCounts() {
    ParsedHistoryCommand undo = WorldEditCommandParser.parseHistoryCommand("//undo 20");
    ParsedHistoryCommand redo =
        WorldEditCommandParser.parseHistoryCommand("/worldedit:redo 7 Steve");
    ParsedHistoryCommand defaultRedo = WorldEditCommandParser.parseHistoryCommand("//redo Steve");

    assertTrue(WorldEditCommandParser.isClipboardCutCommand("//cut"));
    assertTrue(WorldEditCommandParser.isClipboardCutCommand("/worldedit:lazycut"));
    assertFalse(WorldEditCommandParser.isClipboardCutCommand("//copy"));
    assertNotNull(undo);
    assertEquals(HistoryAction.UNDO, undo.action());
    assertEquals(20, undo.steps());
    assertNotNull(redo);
    assertEquals(HistoryAction.REDO, redo.action());
    assertEquals(7, redo.steps());
    assertNotNull(defaultRedo);
    assertEquals(HistoryAction.REDO, defaultRedo.action());
    assertEquals(1, defaultRedo.steps());
  }

  @Test
  void parserSeparatesDestructiveAndEntityOnlyCommands() {
    assertTrue(WorldEditCommandParser.isOperationSnapshotCommand("//regen"));
    assertTrue(WorldEditCommandParser.isOperationSnapshotCommand("/worldedit:regen"));
    assertTrue(WorldEditCommandParser.isOperationSnapshotCommand("//set barrier"));
    assertTrue(WorldEditCommandParser.isOperationSnapshotCommand("/worldedit:replace air stone"));
    assertTrue(WorldEditCommandParser.isOperationSnapshotCommand("//stack 2"));
    assertFalse(WorldEditCommandParser.isOperationSnapshotCommand("//copy"));
    assertFalse(WorldEditCommandParser.isOperationSnapshotCommand("//cut"));
    assertFalse(WorldEditCommandParser.isOperationSnapshotCommand("//paste"));
    assertFalse(WorldEditCommandParser.isOperationSnapshotCommand("//move"));
    assertFalse(WorldEditCommandParser.isOperationSnapshotCommand("//undo"));
    assertFalse(WorldEditCommandParser.isOperationSnapshotCommand("//redo"));

    assertTrue(WorldEditCommandParser.isEntityRefreshCommand("//butcher"));
    assertTrue(WorldEditCommandParser.isEntityRefreshCommand("/worldedit:remove item_display 100"));
    assertTrue(WorldEditCommandParser.isEntityRefreshCommand("//rem item 100"));
    assertTrue(
        WorldEditCommandParser.isEntityRefreshCommand("//rement minecraft:item_display 100"));
    assertFalse(WorldEditCommandParser.isEntityRefreshCommand("//regen"));
  }

  @Test
  void operationSnapshotSuppliesOldMarkerWhenLiveSnapshotMisses() {
    UUID worldId = new UUID(81L, 82L);
    UUID otherWorldId = new UUID(83L, 84L);
    BlockVector3 position = BlockVector3.at(-18, 70, 31);
    MarkerSnapshot terminal =
        new MarkerSnapshot(
            null, new TerminalData("TERMINAL", "NORTH"), null, null, null, false, false);
    MarkerSnapshot storage =
        new MarkerSnapshot(
            new StorageData("live-storage", "BASIC", "SOUTH"),
            null,
            null,
            null,
            null,
            false,
            false);
    PendingOperationSnapshot operationSnapshot =
        new PendingOperationSnapshot(
            worldId,
            Map.of(
                WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()), terminal),
            Set.of(new ChunkKey(worldId, position.x() >> 4, position.z() >> 4)),
            System.currentTimeMillis(),
            3,
            "worldedit_regen");

    assertEquals(
        terminal,
        WorldEditBridge.chooseExistingSnapshot(null, operationSnapshot, worldId, position));
    assertEquals(
        storage,
        WorldEditBridge.chooseExistingSnapshot(storage, operationSnapshot, worldId, position));
    assertNull(
        WorldEditBridge.chooseExistingSnapshot(null, operationSnapshot, otherWorldId, position));
    assertEquals(terminal, operationSnapshot.get(worldId, position));
    assertEquals(
        position.x(),
        WorldEditMarkerMath.blockX(
            WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z())));
    assertEquals(
        position.y(),
        WorldEditMarkerMath.blockY(
            WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z())));
    assertEquals(
        position.z(),
        WorldEditMarkerMath.blockZ(
            WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z())));
  }

  @Test
  void markerHistoryKeepsMoreThanSixteenEntriesWithinTtl() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(11L, 12L);
    UUID worldId = new UUID(13L, 14L);
    MarkerSnapshot[] snapshots = new MarkerSnapshot[20];

    for (int i = 0; i < snapshots.length; i++) {
      snapshots[i] =
          new MarkerSnapshot(
              new StorageData("storage-" + i, "BASIC", "NORTH"),
              null,
              null,
              null,
              null,
              false,
              false);
      history.remember(actorId, null, worldId, 5, 80, 12, snapshots[i]);
    }

    for (int i = snapshots.length - 1; i >= 0; i--) {
      assertEquals(snapshots[i], history.consume(actorId, HistoryAction.UNDO, worldId, 5, 80, 12));
    }
    assertNull(history.consume(actorId, HistoryAction.UNDO, worldId, 5, 80, 12));
  }
}
