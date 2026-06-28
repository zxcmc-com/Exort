package com.zxcmc.exort.chunkloader;

import java.util.ArrayList;
import java.util.List;

public final class ChunkLoaderArea {
  private ChunkLoaderArea() {}

  public static List<ChunkCoord> square(int centerX, int centerZ, int radius) {
    int safeRadius = ChunkLoaderConfig.clampRadius(radius);
    List<ChunkCoord> chunks = new ArrayList<>((safeRadius * 2 + 1) * (safeRadius * 2 + 1));
    for (int x = centerX - safeRadius; x <= centerX + safeRadius; x++) {
      for (int z = centerZ - safeRadius; z <= centerZ + safeRadius; z++) {
        chunks.add(new ChunkCoord(x, z));
      }
    }
    return List.copyOf(chunks);
  }

  public record ChunkCoord(int x, int z) {}
}
