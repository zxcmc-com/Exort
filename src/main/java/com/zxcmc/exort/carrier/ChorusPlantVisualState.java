package com.zxcmc.exort.carrier;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;

public enum ChorusPlantVisualState {
  TERMINAL_MONITOR_BUS_PROXY("exort:proxy", true, true, false, true, true, true),
  STORAGE_PROXY("exort:storage/storage", true, true, true, false, true, true),
  NONE("exort:none", true, true, true, true, true, true);

  public static final List<BlockFace> CHORUS_FACES =
      List.of(
          BlockFace.DOWN,
          BlockFace.EAST,
          BlockFace.NORTH,
          BlockFace.SOUTH,
          BlockFace.UP,
          BlockFace.WEST);

  private final String modelId;
  private final boolean down;
  private final boolean east;
  private final boolean north;
  private final boolean south;
  private final boolean up;
  private final boolean west;

  ChorusPlantVisualState(
      String modelId,
      boolean down,
      boolean east,
      boolean north,
      boolean south,
      boolean up,
      boolean west) {
    this.modelId = modelId;
    this.down = down;
    this.east = east;
    this.north = north;
    this.south = south;
    this.up = up;
    this.west = west;
  }

  public String modelId() {
    return modelId;
  }

  public boolean hasFace(BlockFace face) {
    return switch (face) {
      case DOWN -> down;
      case EAST -> east;
      case NORTH -> north;
      case SOUTH -> south;
      case UP -> up;
      case WEST -> west;
      default -> false;
    };
  }

  public String stateKey() {
    return "down=" + down + ",east=" + east + ",north=" + north + ",south=" + south + ",up=" + up
        + ",west=" + west;
  }

  public BlockData createBlockData() {
    BlockData data = Material.CHORUS_PLANT.createBlockData();
    if (!(data instanceof MultipleFacing facing)) {
      throw new IllegalStateException("minecraft:chorus_plant must support multiple faces");
    }
    applyTo(facing);
    return data;
  }

  public void applyTo(MultipleFacing facing) {
    for (BlockFace face : CHORUS_FACES) {
      facing.setFace(face, hasFace(face));
    }
  }
}
