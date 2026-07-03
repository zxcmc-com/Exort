package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkLoaderInventoryDiffTest {
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final ChunkLoaderType TYPE = ChunkLoaderType.CHUNK_LOADER;

  @Test
  void detectsPlayerMoveIntoExternalInventory() {
    assertEquals(
        List.of(
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.INTO_EXTERNAL, FIRST, TYPE, 1)),
        ChunkLoaderInventoryDiff.diff(
            snapshot(), snapshot(FIRST, 1), snapshot(FIRST, 1), snapshot()));
  }

  @Test
  void detectsPlayerTakeFromExternalInventory() {
    assertEquals(
        List.of(
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.INTO_PLAYER, FIRST, TYPE, 1)),
        ChunkLoaderInventoryDiff.diff(
            snapshot(FIRST, 1), snapshot(), snapshot(), snapshot(FIRST, 1)));
  }

  @Test
  void detectsHotbarSwapBothDirections() {
    assertEquals(
        List.of(
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.INTO_EXTERNAL, FIRST, TYPE, 1),
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.INTO_PLAYER, SECOND, TYPE, 1)),
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
                ChunkLoaderInventoryDiff.Direction.LOST_FROM_EXTERNAL, FIRST, TYPE, 1)),
        ChunkLoaderInventoryDiff.diff(snapshot(FIRST, 1), snapshot(), snapshot(), snapshot()));
  }

  @Test
  void preservesTypeForUnassignedChunkLoaders() {
    ChunkLoaderItemSnapshot.Key personal =
        new ChunkLoaderItemSnapshot.Key(null, ChunkLoaderType.PERSONAL_CHUNK_LOADER);

    assertEquals(
        List.of(
            new ChunkLoaderInventoryDiff.Transfer(
                ChunkLoaderInventoryDiff.Direction.INTO_EXTERNAL,
                null,
                ChunkLoaderType.PERSONAL_CHUNK_LOADER,
                1)),
        ChunkLoaderInventoryDiff.diff(
            snapshot(), snapshot(personal, 1), snapshot(personal, 1), snapshot()));
  }

  private static ChunkLoaderItemSnapshot snapshot() {
    return ChunkLoaderItemSnapshot.empty();
  }

  private static ChunkLoaderItemSnapshot snapshot(UUID id, int amount) {
    return ChunkLoaderItemSnapshot.ofCounts(Map.of(id, amount));
  }

  private static ChunkLoaderItemSnapshot snapshot(ChunkLoaderItemSnapshot.Key key, int amount) {
    return ChunkLoaderItemSnapshot.ofTypedCounts(Map.of(key, amount));
  }
}
