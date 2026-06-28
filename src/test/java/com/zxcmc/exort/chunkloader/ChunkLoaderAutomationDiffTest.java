package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkLoaderAutomationDiffTest {
  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void destinationGainIsAutomationMove() {
    assertEquals(
        List.of(
            new ChunkLoaderAutomationDiff.Result(ChunkLoaderAutomationDiff.Action.MOVED, ID, 1)),
        ChunkLoaderAutomationDiff.diff(
            snapshot(ID, 1), snapshot(ID, 1), snapshot(), snapshot(), snapshot(ID, 1)));
  }

  @Test
  void sourceRestoreIsNotLoggedAsLoss() {
    assertEquals(
        List.of(),
        ChunkLoaderAutomationDiff.diff(
            snapshot(ID, 1), snapshot(ID, 1), snapshot(), snapshot(ID, 1), snapshot()));
  }

  @Test
  void unchangedInventoriesAreNotLoggedAsLoss() {
    assertEquals(
        List.of(),
        ChunkLoaderAutomationDiff.diff(
            snapshot(ID, 1), snapshot(ID, 1), snapshot(), snapshot(ID, 1), snapshot()));
  }

  @Test
  void partialSourceLossDoesNotTreatFullMovingStackAsLost() {
    assertEquals(
        List.of(
            new ChunkLoaderAutomationDiff.Result(ChunkLoaderAutomationDiff.Action.MOVED, ID, 1)),
        ChunkLoaderAutomationDiff.diff(
            snapshot(ID, 64), snapshot(ID, 64), snapshot(), snapshot(ID, 63), snapshot(ID, 1)));
  }

  @Test
  void sourceLossWithoutDestinationGainIsLoss() {
    assertEquals(
        List.of(new ChunkLoaderAutomationDiff.Result(ChunkLoaderAutomationDiff.Action.LOST, ID, 1)),
        ChunkLoaderAutomationDiff.diff(
            snapshot(ID, 1), snapshot(ID, 1), snapshot(), snapshot(), snapshot()));
  }

  @Test
  void emptyMovingSnapshotDoesNothing() {
    assertEquals(
        List.of(),
        ChunkLoaderAutomationDiff.diff(snapshot(), snapshot(), snapshot(), snapshot(), snapshot()));
  }

  private static ChunkLoaderItemSnapshot snapshot() {
    return ChunkLoaderItemSnapshot.empty();
  }

  private static ChunkLoaderItemSnapshot snapshot(UUID id, int amount) {
    return ChunkLoaderItemSnapshot.ofCounts(Map.of(id, amount));
  }
}
