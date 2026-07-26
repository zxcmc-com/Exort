package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Locale;
import java.util.Set;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

final class WorldEditCommandParser {
  enum BlockMutationKind {
    NONE,
    SELECTION,
    LOCAL,
    GENERATION,
    STACK,
    MOVE,
    PASTE,
    CUT
  }

  private static final int DEFAULT_LOCAL_RADIUS = 10;
  private static final Set<String> SELECTION_MUTATION_COMMANDS =
      Set.of(
          "set",
          "replace",
          "re",
          "rep",
          "regen",
          "regenerate",
          "restore",
          "smooth",
          "snowsmooth",
          "naturalize",
          "walls",
          "faces",
          "outline",
          "center",
          "middle",
          "overlay",
          "hollow",
          "deform",
          "forest",
          "flora",
          "line",
          "curve",
          "generate",
          "g",
          "gen",
          "update");
  private static final Set<String> LOCAL_MUTATION_COMMANDS =
      Set.of(
          "replacenear",
          "fixlava",
          "fixwater",
          "drain",
          "fill",
          "fillr",
          "removeabove",
          "removebelow",
          "removenear",
          "snow",
          "thaw",
          "green",
          "extinguish",
          "ex",
          "ext");
  private static final Set<String> GENERATION_MUTATION_COMMANDS =
      Set.of(
          "cyl",
          "hcyl",
          "sphere",
          "hsphere",
          "pyramid",
          "hpyramid",
          "hollowpyramid",
          "cone",
          "forestgen",
          "pumpkins",
          "feature",
          "structure",
          "revolve");
  private static final Set<String> ENTITY_REFRESH_COMMANDS =
      Set.of("butcher", "remove", "rem", "rement");

  private WorldEditCommandParser() {}

  static boolean isClipboardCopyCommand(String arguments) {
    String command = commandName(arguments);
    return "copy".equals(command)
        || "cut".equals(command)
        || "lazycopy".equals(command)
        || "lazycut".equals(command);
  }

  static boolean isClipboardCutCommand(String arguments) {
    String command = commandName(arguments);
    return "cut".equals(command) || "lazycut".equals(command);
  }

  static boolean isClipboardPasteCommand(String arguments) {
    return "paste".equals(commandName(arguments));
  }

  static boolean isClipboardClearCommand(String arguments) {
    String command = commandName(arguments);
    return "clearclipboard".equals(command) || "clearclipboard".equals(command.replace("-", ""));
  }

  static boolean invalidatesClipboardTrust(String arguments) {
    return isClipboardCopyCommand(arguments)
        || isClipboardClearCommand(arguments)
        || isSchematicLoadCommand(arguments);
  }

  static boolean isSchematicLoadCommand(String arguments) {
    return schematicName(arguments, "load") != null;
  }

  static String schematicName(String arguments, String operation) {
    String command = commandName(arguments);
    if ((!"schematic".equals(command) && !"schem".equals(command))
        || operation == null
        || operation.isBlank()) {
      return null;
    }
    String[] tokens = nonFlagTokens(commandRemainder(arguments));
    if (tokens.length < 2 || !operation.equalsIgnoreCase(tokens[0])) {
      return null;
    }
    String name = tokens[tokens.length - 1].trim().toLowerCase(Locale.ROOT);
    if (name.endsWith(".schematic")) {
      name = name.substring(0, name.length() - ".schematic".length());
    } else if (name.endsWith(".schem")) {
      name = name.substring(0, name.length() - ".schem".length());
    }
    return name.isBlank() ? null : name;
  }

  static HistoryAction parseHistoryAction(String arguments) {
    ParsedHistoryCommand command = parseHistoryCommand(arguments);
    return command == null ? null : command.action();
  }

  static ParsedHistoryCommand parseHistoryCommand(String arguments) {
    String command = commandName(arguments);
    HistoryAction action =
        switch (command) {
          case "undo" -> HistoryAction.UNDO;
          case "redo" -> HistoryAction.REDO;
          default -> null;
        };
    if (action == null) {
      return null;
    }
    return new ParsedHistoryCommand(action, parseHistorySteps(commandRemainder(arguments)));
  }

  private static int parseHistorySteps(String remainder) {
    if (remainder == null || remainder.isBlank()) {
      return 1;
    }
    for (String token : remainder.split("\\s+")) {
      if (token.isBlank() || token.startsWith("-")) {
        continue;
      }
      try {
        long value = Long.parseLong(token);
        if (value <= 0L) {
          return 1;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
      } catch (NumberFormatException ignored) {
        // WorldEdit accepts an optional player argument after the count.
      }
    }
    return 1;
  }

  static boolean isMoveCommand(String arguments) {
    return "move".equals(commandName(arguments));
  }

  static boolean isOperationSnapshotCommand(String arguments) {
    BlockMutationKind kind = blockMutationKind(arguments);
    return kind == BlockMutationKind.SELECTION
        || kind == BlockMutationKind.LOCAL
        || kind == BlockMutationKind.GENERATION
        || kind == BlockMutationKind.STACK;
  }

  static boolean isEntityRefreshCommand(String arguments) {
    return ENTITY_REFRESH_COMMANDS.contains(commandName(arguments));
  }

  static boolean isBroadOperationSnapshotCommand(String arguments) {
    BlockMutationKind kind = blockMutationKind(arguments);
    return kind == BlockMutationKind.LOCAL
        || kind == BlockMutationKind.GENERATION
        || kind == BlockMutationKind.STACK;
  }

  static BlockMutationKind blockMutationKind(String arguments) {
    String command = commandName(arguments);
    if ("move".equals(command)) return BlockMutationKind.MOVE;
    if ("stack".equals(command)) return BlockMutationKind.STACK;
    if ("paste".equals(command)) return BlockMutationKind.PASTE;
    if ("cut".equals(command) || "lazycut".equals(command)) return BlockMutationKind.CUT;
    if (SELECTION_MUTATION_COMMANDS.contains(command)) return BlockMutationKind.SELECTION;
    if (LOCAL_MUTATION_COMMANDS.contains(command)) return BlockMutationKind.LOCAL;
    if (GENERATION_MUTATION_COMMANDS.contains(command)) return BlockMutationKind.GENERATION;
    return BlockMutationKind.NONE;
  }

  static WorldEditBounds affectedBounds(
      String arguments,
      WorldEditBounds selection,
      BlockVector3 placement,
      BlockVector3 stackDirection) {
    String command = commandName(arguments);
    BlockMutationKind kind = blockMutationKind(arguments);
    if (kind == BlockMutationKind.SELECTION) return selection;
    String[] tokens = nonFlagTokens(commandRemainder(arguments));
    if (kind == BlockMutationKind.STACK) {
      return stackBounds(selection, tokens, stackDirection);
    }
    if (kind == BlockMutationKind.GENERATION) {
      return generationBounds(command, tokens, selection, placement);
    }
    if (kind != BlockMutationKind.LOCAL) {
      return null;
    }
    return switch (command) {
      case "replacenear", "fixlava", "fixwater", "drain" -> {
        Integer radius = firstPositiveInteger(tokens, 0);
        yield radius == null || placement == null
            ? null
            : WorldEditBounds.around(placement, radius, radius);
      }
      case "fill", "fillr" -> {
        Integer radius = firstPositiveInteger(tokens, 1);
        Integer depth = firstPositiveInteger(tokens, 2);
        yield radius == null || placement == null
            ? null
            : WorldEditBounds.around(placement, radius, depth == null ? radius : depth);
      }
      case "removeabove", "removebelow" -> {
        Integer radius = firstPositiveInteger(tokens, 0);
        Integer height = firstPositiveInteger(tokens, 1);
        int resolvedRadius = radius == null ? DEFAULT_LOCAL_RADIUS : radius;
        int resolvedHeight = height == null ? resolvedRadius : height;
        yield placement == null
            ? null
            : WorldEditBounds.around(placement, resolvedRadius, resolvedHeight);
      }
      case "removenear" -> {
        Integer radius = firstPositiveInteger(tokens, 1);
        yield placement == null
            ? null
            : WorldEditBounds.around(
                placement,
                radius == null ? DEFAULT_LOCAL_RADIUS : radius,
                radius == null ? DEFAULT_LOCAL_RADIUS : radius);
      }
      case "snow", "thaw", "green", "extinguish" -> {
        Integer radius = firstPositiveInteger(tokens, 0);
        int resolved = radius == null ? DEFAULT_LOCAL_RADIUS : radius;
        yield placement == null ? null : WorldEditBounds.around(placement, resolved, resolved);
      }
      default -> null;
    };
  }

  private static WorldEditBounds generationBounds(
      String command, String[] tokens, WorldEditBounds selection, BlockVector3 placement) {
    if ("generate".equals(command)) {
      return selection;
    }
    if ("g".equals(command) || "gen".equals(command)) {
      return selection;
    }
    if ("revolve".equals(command)) {
      return revolveBounds(selection, placement);
    }
    if (placement == null) {
      return null;
    }
    int horizontal =
        switch (command) {
          case "cyl", "hcyl", "sphere", "hsphere", "pyramid", "hpyramid", "hollowpyramid", "cone" ->
              positiveMagnitude(tokens, 1, DEFAULT_LOCAL_RADIUS);
          case "forestgen", "pumpkins" -> positiveMagnitude(tokens, 0, DEFAULT_LOCAL_RADIUS);
          default -> maxPositiveMagnitude(tokens, DEFAULT_LOCAL_RADIUS);
        };
    int vertical =
        switch (command) {
          case "cyl", "hcyl", "cone" -> {
            Integer parsed = firstPositiveInteger(tokens, 2);
            yield parsed == null ? horizontal : parsed;
          }
          case "feature", "structure" -> Math.max(DEFAULT_LOCAL_RADIUS, horizontal);
          default -> horizontal;
        };
    return WorldEditBounds.around(placement, horizontal, vertical);
  }

  private static WorldEditBounds revolveBounds(WorldEditBounds selection, BlockVector3 placement) {
    if (selection == null || placement == null) {
      return null;
    }
    int horizontal =
        maxSaturatedDistance(
            placement.x(),
            placement.z(),
            selection.minimum().x(),
            selection.minimum().z(),
            selection.maximum().x(),
            selection.maximum().z());
    int vertical =
        Math.max(
            saturatedAbsoluteDifference(placement.y(), selection.minimum().y()),
            saturatedAbsoluteDifference(placement.y(), selection.maximum().y()));
    return WorldEditBounds.around(placement, horizontal, vertical);
  }

  private static int maxSaturatedDistance(
      int centerX, int centerZ, int minX, int minZ, int maxX, int maxZ) {
    return Math.max(
        Math.max(
            saturatedAbsoluteDifference(centerX, minX), saturatedAbsoluteDifference(centerX, maxX)),
        Math.max(
            saturatedAbsoluteDifference(centerZ, minZ),
            saturatedAbsoluteDifference(centerZ, maxZ)));
  }

  private static int saturatedAbsoluteDifference(int first, int second) {
    long difference = Math.abs((long) first - second);
    return difference > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) difference;
  }

  static BlockVector3 parseStackDirection(String arguments, Player player) {
    String[] tokens = nonFlagTokens(commandRemainder(arguments));
    for (int index = 0; index < tokens.length; index++) {
      if (index == 0 && parsePositiveInteger(tokens[index]) != null) continue;
      BlockFace direction = directionToken(tokens[index], player);
      if (direction != null) return vectorFor(direction, 1);
    }
    BlockFace direction = directionFromPlayer(player);
    return direction == null ? null : vectorFor(direction, 1);
  }

  static Integer parseStackCount(String arguments) {
    if (blockMutationKind(arguments) != BlockMutationKind.STACK) {
      return null;
    }
    String[] tokens = nonFlagTokens(commandRemainder(arguments));
    if (tokens.length == 0) {
      return 1;
    }
    Integer count = firstPositiveInteger(tokens, 0);
    return count == null && tokens.length > 0 && tokens[0].matches("[+-]?\\d+") ? null : count;
  }

  static String commandSignature(String arguments) {
    String command = commandName(arguments);
    if (command.isBlank()) return "";
    String remainder = commandRemainder(arguments).replaceAll("\\s+", " ").trim();
    return remainder.isEmpty() ? command : command + " " + remainder;
  }

  static PendingPasteCommand parsePasteCommand(String arguments) {
    boolean atOrigin = false;
    boolean onlySelect = false;
    String remainder = commandRemainder(arguments);
    if (!remainder.isBlank()) {
      for (String token : remainder.split("\\s+")) {
        if (token.length() <= 1 || !token.startsWith("-") || token.startsWith("--")) {
          continue;
        }
        for (int i = 1; i < token.length(); i++) {
          char flag = token.charAt(i);
          if (flag == 'o') {
            atOrigin = true;
          } else if (flag == 'n') {
            onlySelect = true;
          }
        }
      }
    }
    return new PendingPasteCommand(atOrigin, onlySelect, System.currentTimeMillis(), 3);
  }

  static BlockVector3 parseMoveVector(String arguments, Player player) {
    int distance = 1;
    boolean distanceSet = false;
    BlockFace direction = null;
    String remainder = commandRemainder(arguments);
    if (!remainder.isBlank()) {
      for (String token : remainder.split("\\s+")) {
        if (token.isBlank() || token.startsWith("-")) {
          continue;
        }
        if (!distanceSet) {
          try {
            distance = Integer.parseInt(token);
            distanceSet = true;
            continue;
          } catch (NumberFormatException ignored) {
            // Token may be a direction, for example //move north.
          }
        }
        if (direction == null) {
          direction = directionToken(token, player);
          if (direction != null) {
            continue;
          }
        }
        break;
      }
    }
    if (direction == null) {
      direction = directionFromPlayer(player);
    }
    return vectorFor(direction, distance);
  }

  private static WorldEditBounds stackBounds(
      WorldEditBounds selection, String[] tokens, BlockVector3 direction) {
    if (selection == null || direction == null) return null;
    Integer parsedCount = firstPositiveInteger(tokens, 0);
    if (parsedCount == null && tokens.length > 0 && tokens[0].matches("[+-]?\\d+")) {
      return null;
    }
    int count = parsedCount == null ? 1 : parsedCount;
    int dx = WorldEditBounds.saturatedMultiply(selection.sizeX(), direction.x());
    int dy = WorldEditBounds.saturatedMultiply(selection.sizeY(), direction.y());
    int dz = WorldEditBounds.saturatedMultiply(selection.sizeZ(), direction.z());
    BlockVector3 offset =
        BlockVector3.at(
            WorldEditBounds.saturatedMultiply(dx, count),
            WorldEditBounds.saturatedMultiply(dy, count),
            WorldEditBounds.saturatedMultiply(dz, count));
    return selection.union(selection.translate(offset));
  }

  private static String[] nonFlagTokens(String remainder) {
    if (remainder == null || remainder.isBlank()) return new String[0];
    return java.util.Arrays.stream(remainder.split("\\s+"))
        .filter(token -> !token.isBlank() && !token.startsWith("-"))
        .toArray(String[]::new);
  }

  private static Integer firstPositiveInteger(String[] tokens, int index) {
    return tokens == null || index < 0 || index >= tokens.length
        ? null
        : parsePositiveInteger(tokens[index]);
  }

  private static Integer parsePositiveInteger(String token) {
    try {
      long value = Long.parseLong(token);
      return value <= 0L || value > Integer.MAX_VALUE ? null : (int) value;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static int maxPositiveMagnitude(String[] tokens, int fallback) {
    int maximum = Math.max(1, fallback);
    if (tokens == null) {
      return maximum;
    }
    for (String token : tokens) {
      if (token == null || token.isBlank()) continue;
      for (String component : token.split(",")) {
        Integer parsed = parsePositiveInteger(component);
        if (parsed != null) {
          maximum = Math.max(maximum, parsed);
        }
      }
    }
    return maximum;
  }

  private static int positiveMagnitude(String[] tokens, int index, int fallback) {
    if (tokens == null || index < 0 || index >= tokens.length) {
      return Math.max(1, fallback);
    }
    int maximum = 0;
    for (String component : tokens[index].split(",")) {
      Integer parsed = parsePositiveInteger(component);
      if (parsed != null) {
        maximum = Math.max(maximum, parsed);
      }
    }
    return maximum == 0 ? Math.max(1, fallback) : maximum;
  }

  private static String commandName(String arguments) {
    if (arguments == null) return "";
    String command = arguments.trim();
    while (command.startsWith("/")) {
      command = command.substring(1);
    }
    if (command.isBlank()) return "";
    int space = command.indexOf(' ');
    if (space >= 0) {
      command = command.substring(0, space);
    }
    int colon = command.lastIndexOf(':');
    if (colon >= 0 && colon + 1 < command.length()) {
      command = command.substring(colon + 1);
      while (command.startsWith("/")) {
        command = command.substring(1);
      }
    }
    return command.toLowerCase(Locale.ROOT);
  }

  private static String commandRemainder(String arguments) {
    if (arguments == null) return "";
    String command = arguments.trim();
    while (command.startsWith("/")) {
      command = command.substring(1);
    }
    int space = command.indexOf(' ');
    return space < 0 ? "" : command.substring(space + 1).trim();
  }

  private static BlockFace directionToken(String token, Player player) {
    if (token == null) return null;
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "n", "north" -> BlockFace.NORTH;
      case "s", "south" -> BlockFace.SOUTH;
      case "e", "east" -> BlockFace.EAST;
      case "w", "west" -> BlockFace.WEST;
      case "u", "up" -> BlockFace.UP;
      case "d", "down" -> BlockFace.DOWN;
      case "me", "forward", "forwards" -> directionFromPlayer(player);
      case "back", "backward", "backwards" -> opposite(directionFromPlayer(player));
      case "left" -> rotateLeft(directionFromPlayer(player));
      case "right" -> rotateRight(directionFromPlayer(player));
      default -> null;
    };
  }

  private static BlockFace directionFromPlayer(Player player) {
    if (player == null) return BlockFace.SOUTH;
    float pitch = player.getLocation().getPitch();
    if (pitch <= -60.0f) return BlockFace.UP;
    if (pitch >= 60.0f) return BlockFace.DOWN;
    float yaw = player.getLocation().getYaw() % 360.0f;
    if (yaw < 0.0f) yaw += 360.0f;
    if (yaw < 45.0f || yaw >= 315.0f) return BlockFace.SOUTH;
    if (yaw < 135.0f) return BlockFace.WEST;
    if (yaw < 225.0f) return BlockFace.NORTH;
    return BlockFace.EAST;
  }

  private static BlockFace opposite(BlockFace face) {
    return switch (face) {
      case NORTH -> BlockFace.SOUTH;
      case SOUTH -> BlockFace.NORTH;
      case EAST -> BlockFace.WEST;
      case WEST -> BlockFace.EAST;
      case UP -> BlockFace.DOWN;
      case DOWN -> BlockFace.UP;
      default -> BlockFace.SOUTH;
    };
  }

  private static BlockFace rotateLeft(BlockFace face) {
    return switch (face) {
      case NORTH -> BlockFace.WEST;
      case WEST -> BlockFace.SOUTH;
      case SOUTH -> BlockFace.EAST;
      case EAST -> BlockFace.NORTH;
      default -> face;
    };
  }

  private static BlockFace rotateRight(BlockFace face) {
    return switch (face) {
      case NORTH -> BlockFace.EAST;
      case EAST -> BlockFace.SOUTH;
      case SOUTH -> BlockFace.WEST;
      case WEST -> BlockFace.NORTH;
      default -> face;
    };
  }

  private static BlockVector3 vectorFor(BlockFace face, int distance) {
    if (face == null || distance == 0) return BlockVector3.at(0, 0, 0);
    return switch (face) {
      case NORTH -> BlockVector3.at(0, 0, -distance);
      case SOUTH -> BlockVector3.at(0, 0, distance);
      case EAST -> BlockVector3.at(distance, 0, 0);
      case WEST -> BlockVector3.at(-distance, 0, 0);
      case UP -> BlockVector3.at(0, distance, 0);
      case DOWN -> BlockVector3.at(0, -distance, 0);
      default -> BlockVector3.at(0, 0, 0);
    };
  }
}
