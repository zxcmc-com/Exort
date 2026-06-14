package com.zxcmc.exort.display;

import com.zxcmc.exort.breaking.BreakType;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

public final class BreakingOverlayModelResolver {
  private final Plugin plugin;

  public BreakingOverlayModelResolver(
      Plugin plugin,
      Material wireCarrierMaterial,
      Material terminalMaterial,
      Material storageCarrier,
      Material monitorCarrier,
      Material busCarrier) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(wireCarrierMaterial, "wireCarrierMaterial");
    Objects.requireNonNull(terminalMaterial, "terminalMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(monitorCarrier, "monitorCarrier");
    Objects.requireNonNull(busCarrier, "busCarrier");
  }

  public String modelKey(Block block, BreakType type) {
    if (block == null || type == null) {
      return null;
    }
    return switch (type) {
      case STORAGE -> "storage/core";
      case TERMINAL ->
          "terminal/" + key(horizontalOrSouth(TerminalMarker.facing(plugin, block).orElse(null)));
      case MONITOR ->
          "terminal/" + key(horizontalOrSouth(MonitorMarker.facing(plugin, block).orElse(null)));
      case BUS ->
          "bus/"
              + key(
                  fullOrNorth(
                      BusMarker.get(plugin, block).map(BusMarker.Data::facing).orElse(null)));
      case WIRE -> "wire/center";
      case NONE -> null;
    };
  }

  private static BlockFace horizontalOrSouth(BlockFace face) {
    if (face == null) {
      return BlockFace.SOUTH;
    }
    return switch (face) {
      case NORTH, SOUTH, EAST, WEST -> face;
      default -> BlockFace.SOUTH;
    };
  }

  private static BlockFace fullOrNorth(BlockFace face) {
    if (face == null) {
      return BlockFace.NORTH;
    }
    return switch (face) {
      case UP, DOWN, NORTH, SOUTH, EAST, WEST -> face;
      default -> BlockFace.NORTH;
    };
  }

  private static String key(BlockFace face) {
    return face.name().toLowerCase(Locale.ROOT);
  }
}
