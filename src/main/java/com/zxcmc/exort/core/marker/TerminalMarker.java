package com.zxcmc.exort.core.marker;

import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/** Chunk-level marker for terminal blocks (all modes). */
public final class TerminalMarker {
  private TerminalMarker() {}

  private static final String SECTION = "terminal";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_FACING = "facing";

  public static void set(Plugin plugin, Block block) {
    set(plugin, block, TerminalKind.TERMINAL, null);
  }

  public static void set(Plugin plugin, Block block, BlockFace facing) {
    set(plugin, block, TerminalKind.TERMINAL, facing);
  }

  public static void set(Plugin plugin, Block block, TerminalKind kind, BlockFace facing) {
    TerminalKind safeKind = kind == null ? TerminalKind.TERMINAL : kind;
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_TYPE, safeKind.name());
    if (facing != null) {
      ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_FACING, facing.name());
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_FACING);
    }
  }

  public static boolean isTerminal(Plugin plugin, Block block) {
    return ChunkMarkerStore.hasSection(plugin, block, SECTION);
  }

  public static boolean isKind(Plugin plugin, Block block, TerminalKind kind) {
    return kind(plugin, block) == kind;
  }

  public static TerminalKind kind(Plugin plugin, Block block) {
    String raw =
        ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_TYPE)
            .orElse(TerminalKind.TERMINAL.name());
    return parseType(raw, TerminalKind.TERMINAL);
  }

  public static Optional<BlockFace> facing(Plugin plugin, Block block) {
    String raw = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_FACING).orElse(null);
    if (raw == null) return Optional.empty();
    try {
      return Optional.of(BlockFace.valueOf(raw));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }

  private static TerminalKind parseType(String raw, TerminalKind fallback) {
    if (raw == null) return fallback;
    try {
      return TerminalKind.valueOf(raw);
    } catch (IllegalArgumentException ignored) {
      return fallback;
    }
  }
}
