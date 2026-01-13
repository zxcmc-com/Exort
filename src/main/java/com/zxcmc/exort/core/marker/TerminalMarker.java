package com.zxcmc.exort.core.marker;

import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/** Chunk-level marker for terminal blocks (all modes). */
public final class TerminalMarker {
  private TerminalMarker() {}

  private static final String PREFIX = "terminal";
  private static final String KEY_TYPE = "type";
  private static final String KEY_FACING = "facing";

  public static void set(Plugin plugin, Block block) {
    set(plugin, block, TerminalKind.TERMINAL, null);
  }

  public static void set(Plugin plugin, Block block, BlockFace facing) {
    set(plugin, block, TerminalKind.TERMINAL, facing);
  }

  public static void set(Plugin plugin, Block block, TerminalKind kind, BlockFace facing) {
    StringBuilder value = new StringBuilder();
    TerminalKind safeKind = kind == null ? TerminalKind.TERMINAL : kind;
    value.append(KEY_TYPE).append(":").append(safeKind.name());
    if (facing != null) {
      value.append(";").append(KEY_FACING).append(":").append(facing.name());
    }
    ChunkMarkerStore.setMarker(plugin, PREFIX, block, value.toString());
  }

  public static boolean isTerminal(Plugin plugin, Block block) {
    return ChunkMarkerStore.getMarker(plugin, PREFIX, block).isPresent();
  }

  public static boolean isKind(Plugin plugin, Block block, TerminalKind kind) {
    return kind(plugin, block) == kind;
  }

  public static TerminalKind kind(Plugin plugin, Block block) {
    return ChunkMarkerStore.getMarker(plugin, PREFIX, block)
        .map(raw -> parseType(raw, TerminalKind.TERMINAL))
        .orElse(TerminalKind.TERMINAL);
  }

  public static Optional<BlockFace> facing(Plugin plugin, Block block) {
    return ChunkMarkerStore.getMarker(plugin, PREFIX, block)
        .flatMap(
            raw -> {
              if (raw == null) {
                return Optional.empty();
              }
              for (String part : raw.split(";")) {
                if (!part.startsWith(KEY_FACING + ":")) continue;
                String dir = part.substring((KEY_FACING + ":").length());
                try {
                  return Optional.of(BlockFace.valueOf(dir));
                } catch (IllegalArgumentException ignored) {
                  return Optional.empty();
                }
              }
              return Optional.empty();
            });
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearMarker(plugin, PREFIX, block);
  }

  private static TerminalKind parseType(String raw, TerminalKind fallback) {
    if (raw == null) return fallback;
    for (String part : raw.split(";")) {
      if (!part.startsWith(KEY_TYPE + ":")) continue;
      String value = part.substring((KEY_TYPE + ":").length());
      try {
        return TerminalKind.valueOf(value);
      } catch (IllegalArgumentException ignored) {
        return fallback;
      }
    }
    // Legacy markers without explicit type
    return fallback;
  }
}
