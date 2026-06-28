package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ChunkLoaderAreaTest {
  @Test
  void radiusZeroOnlyIncludesCenterChunk() {
    assertEquals(
        Set.of(new ChunkLoaderArea.ChunkCoord(4, -2)),
        Set.copyOf(ChunkLoaderArea.square(4, -2, 0)));
  }

  @Test
  void radiusOneBuildsThreeByThreeSquareAroundCenter() {
    assertEquals(
        Set.of(
            new ChunkLoaderArea.ChunkCoord(3, -3),
            new ChunkLoaderArea.ChunkCoord(3, -2),
            new ChunkLoaderArea.ChunkCoord(3, -1),
            new ChunkLoaderArea.ChunkCoord(4, -3),
            new ChunkLoaderArea.ChunkCoord(4, -2),
            new ChunkLoaderArea.ChunkCoord(4, -1),
            new ChunkLoaderArea.ChunkCoord(5, -3),
            new ChunkLoaderArea.ChunkCoord(5, -2),
            new ChunkLoaderArea.ChunkCoord(5, -1)),
        Set.copyOf(ChunkLoaderArea.square(4, -2, 1)));
  }
}
