package com.zxcmc.exort.display;

import org.bukkit.block.BlockFace;
import org.joml.Quaternionf;

public final class DisplayRotation {
  private DisplayRotation() {}

  public static Quaternionf rotationForFacing(BlockFace facing) {
    BlockFace face = facing == null ? BlockFace.SOUTH : facing;
    float rad;
    switch (face) {
      case NORTH -> rad = (float) Math.PI;
      case EAST -> rad = (float) (Math.PI / 2.0);
      case WEST -> rad = (float) (-Math.PI / 2.0);
      default -> rad = 0f; // SOUTH
    }
    return new Quaternionf().rotateY(rad);
  }

  public static Quaternionf rotationForFacingFull(BlockFace facing) {
    if (facing == null) {
      return rotationForFacing(BlockFace.SOUTH);
    }
    return switch (facing) {
      case UP -> new Quaternionf().rotateX((float) (-Math.PI / 2.0));
      case DOWN -> new Quaternionf().rotateX((float) (Math.PI / 2.0));
      case NORTH -> new Quaternionf().rotateY((float) Math.PI);
      case EAST -> new Quaternionf().rotateY((float) (Math.PI / 2.0));
      case WEST -> new Quaternionf().rotateY((float) (-Math.PI / 2.0));
      default -> new Quaternionf();
    };
  }
}
