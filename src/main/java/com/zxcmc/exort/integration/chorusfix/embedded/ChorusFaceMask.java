package com.zxcmc.exort.integration.chorusfix.embedded;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;

public record ChorusFaceMask(
    boolean north, boolean east, boolean south, boolean west, boolean up, boolean down) {
  public static final ChorusFaceMask NONE =
      new ChorusFaceMask(false, false, false, false, false, false);
  public static final ChorusFaceMask ALL = new ChorusFaceMask(true, true, true, true, true, true);

  public static Optional<ChorusFaceMask> fromBlockData(BlockData blockData) {
    if (!(blockData instanceof MultipleFacing facing)) {
      return Optional.empty();
    }
    return Optional.of(
        new ChorusFaceMask(
            facing.hasFace(BlockFace.NORTH),
            facing.hasFace(BlockFace.EAST),
            facing.hasFace(BlockFace.SOUTH),
            facing.hasFace(BlockFace.WEST),
            facing.hasFace(BlockFace.UP),
            facing.hasFace(BlockFace.DOWN)));
  }

  public void applyTo(MultipleFacing facing) {
    facing.setFace(BlockFace.NORTH, north);
    facing.setFace(BlockFace.EAST, east);
    facing.setFace(BlockFace.SOUTH, south);
    facing.setFace(BlockFace.WEST, west);
    facing.setFace(BlockFace.UP, up);
    facing.setFace(BlockFace.DOWN, down);
  }

  public boolean hasHorizontal() {
    return north || east || south || west;
  }

  public boolean isImpossibleCustomCarrier() {
    return up && down && hasHorizontal();
  }

  public String asConfigToken() {
    StringBuilder builder = new StringBuilder();
    append(builder, "north", north);
    append(builder, "east", east);
    append(builder, "south", south);
    append(builder, "west", west);
    append(builder, "up", up);
    append(builder, "down", down);
    return builder.isEmpty() ? "none" : builder.toString();
  }

  public static ChorusFaceMask parse(String raw) {
    String value = Objects.requireNonNull(raw, "raw").trim().toLowerCase(Locale.ROOT);
    if (value.isEmpty() || value.equals("none")) {
      return NONE;
    }
    if (value.equals("all")) {
      return ALL;
    }
    if (value.matches("[01]{6}")) {
      return new ChorusFaceMask(
          value.charAt(0) == '1',
          value.charAt(1) == '1',
          value.charAt(2) == '1',
          value.charAt(3) == '1',
          value.charAt(4) == '1',
          value.charAt(5) == '1');
    }

    boolean north = false;
    boolean east = false;
    boolean south = false;
    boolean west = false;
    boolean up = false;
    boolean down = false;
    for (String token : value.split("[,|\\s]+")) {
      if (token.isBlank()) {
        continue;
      }
      switch (token) {
        case "north", "n" -> north = true;
        case "east", "e" -> east = true;
        case "south", "s" -> south = true;
        case "west", "w" -> west = true;
        case "up", "u" -> up = true;
        case "down", "d" -> down = true;
        default -> throw new IllegalArgumentException("Unknown chorus face token: " + token);
      }
    }
    return new ChorusFaceMask(north, east, south, west, up, down);
  }

  private static void append(StringBuilder builder, String token, boolean enabled) {
    if (!enabled) {
      return;
    }
    if (!builder.isEmpty()) {
      builder.append(',');
    }
    builder.append(token);
  }
}
