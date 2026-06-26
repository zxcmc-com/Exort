package com.zxcmc.exort.integration.worldedit;

final class WorldEditMarkerMath {
  private static final int Y_OFFSET = 2048;

  private WorldEditMarkerMath() {}

  static long blockKey(int x, int y, int z) {
    int yIndex = y + Y_OFFSET;
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (yIndex & 0xFFF);
  }

  static int blockX(long key) {
    return signExtend26((int) ((key >>> 38) & 0x3FFFFFFL));
  }

  static int blockY(long key) {
    return (int) (key & 0xFFFL) - Y_OFFSET;
  }

  static int blockZ(long key) {
    return signExtend26((int) ((key >>> 12) & 0x3FFFFFFL));
  }

  private static int signExtend26(int value) {
    return (value << 6) >> 6;
  }
}
