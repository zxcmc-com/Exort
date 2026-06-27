package com.zxcmc.exort.display.wire;

import org.bukkit.block.BlockFace;

final class WireModelKeys {
  static final BlockFace[] CONNECTION_FACES =
      new BlockFace[] {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
      };

  private WireModelKeys() {}

  static int bit(BlockFace face) {
    return 1 << faceIndex(face);
  }

  static String compactModelKeyForMask(int mask) {
    if (mask == 0) {
      return "center";
    }
    StringBuilder sb = new StringBuilder(6);
    if ((mask & bit(BlockFace.UP)) != 0) sb.append('u');
    if ((mask & bit(BlockFace.DOWN)) != 0) sb.append('d');
    if ((mask & bit(BlockFace.NORTH)) != 0) sb.append('n');
    if ((mask & bit(BlockFace.SOUTH)) != 0) sb.append('s');
    if ((mask & bit(BlockFace.EAST)) != 0) sb.append('e');
    if ((mask & bit(BlockFace.WEST)) != 0) sb.append('w');
    return sb.toString();
  }

  private static int faceIndex(BlockFace face) {
    return switch (face) {
      case UP -> 0;
      case DOWN -> 1;
      case NORTH -> 2;
      case SOUTH -> 3;
      case EAST -> 4;
      case WEST -> 5;
      default -> throw new IllegalArgumentException("Unsupported face: " + face);
    };
  }
}
