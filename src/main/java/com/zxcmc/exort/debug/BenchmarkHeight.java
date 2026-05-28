package com.zxcmc.exort.debug;

import org.bukkit.World;

final class BenchmarkHeight {
  static final int TOP_MARGIN_BLOCKS = 15;
  static final int MIN_BOTTOM_MARGIN_BLOCKS = 4;
  static final double ENTITY_TOP_HEADROOM_BLOCKS = 4.0;

  private BenchmarkHeight() {}

  static int blockY(World world) {
    if (world == null) {
      return 0;
    }
    return blockY(world.getMinHeight(), world.getMaxHeight());
  }

  static int blockY(int minHeight, int maxHeight) {
    int min = minHeight + MIN_BOTTOM_MARGIN_BLOCKS;
    int preferred = maxHeight - TOP_MARGIN_BLOCKS;
    int max = maxHeight - TOP_MARGIN_BLOCKS;
    if (max < min) {
      return min;
    }
    return Math.max(min, Math.min(max, preferred));
  }

  static double entityY(World world, double offset) {
    if (world == null) {
      return offset;
    }
    double min = world.getMinHeight() + MIN_BOTTOM_MARGIN_BLOCKS;
    double max = world.getMaxHeight() - ENTITY_TOP_HEADROOM_BLOCKS;
    double preferred = blockY(world) + offset;
    if (max < min) {
      return min;
    }
    return Math.max(min, Math.min(max, preferred));
  }
}
