package com.zxcmc.exort.integration.worldedit;

final class WorldEditMarkerMath {
  private static final int Y_OFFSET = 2048;

  private WorldEditMarkerMath() {}

  static long blockKey(int x, int y, int z) {
    int yIndex = y + Y_OFFSET;
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (yIndex & 0xFFF);
  }
}
