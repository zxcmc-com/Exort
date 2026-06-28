package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkLoaderInventoryDiffTest {
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void detectsPlayerMoveIntoExternalInventory() {
    assertEquals(
        List.of(
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.INTO_EXTERNAL, FIRST, 1)),
        ChunkLoaderInventoryDiff.diff(
            snapshot(), snapshot(FIRST, 1), snapshot(FIRST, 1), snapshot()));
  }

  @Test
  void detectsPlayerTakeFromExternalInventory() {
    assertEquals(
        List.of(
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.INTO_PLAYER, FIRST, 1)),
        ChunkLoaderInventoryDiff.diff(
            snapshot(FIRST, 1), snapshot(), snapshot(), snapshot(FIRST, 1)));
  }

  @Test
  void detectsHotbarSwapBothDirections() {
    assertEquals(
        List.of(
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.INTO_EXTERNAL, FIRST, 1),
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.INTO_PLAYER, SECOND, 1)),
        ChunkLoaderInventoryDiff.diff(
            snapshot(SECOND, 1), snapshot(FIRST, 1), snapshot(FIRST, 1), snapshot(SECOND, 1)));
  }

  @Test
  void ignoresInternalMovesWhenBoundaryCountsDoNotChange() {
    assertEquals(
        List.of(),
        ChunkLoaderInventoryDiff.diff(
            snapshot(FIRST, 1), snapshot(SECOND, 1), snapshot(FIRST, 1), snapshot(SECOND, 1)));
  }

  @Test
  void externalLossWithoutPlayerGainIsDisappearance() {
    assertEquals(
        List.of(
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.LOST_FROM_EXTERNAL, FIRST, 1)),
        ChunkLoaderInventoryDiff.diff(snapshot(FIRST, 1), snapshot(), snapshot(), snapshot()));
  }

  private static ChunkLoaderItemSnapshot snapshot() {
    return ChunkLoaderItemSnapshot.empty();
  }

  private static ChunkLoaderItemSnapshot snapshot(UUID id, int amount) {
    return ChunkLoaderItemSnapshot.ofCounts(Map.of(id, amount));
  }
}
