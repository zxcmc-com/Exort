package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Locale;
import java.util.Set;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

final class WorldEditCommandParser {
  private static final Set<String> OPERATION_SNAPSHOT_COMMANDS =
      Set.of(
          "set",
          "replace",
          "replacenear",
          "regen",
          "smooth",
          "snowsmooth",
          "naturalize",
          "walls",
          "faces",
          "outline",
          "center",
          "overlay",
          "hollow",
          "deform",
          "forest",
          "flora",
          "green",
          "snow",
          "thaw",
          "fixlava",
          "fixwater",
          "drain",
          "fill",
          "fillr",
          "line",
          "curve",
          "stack");
  private static final Set<String> ENTITY_REFRESH_COMMANDS =
      Set.of("butcher", "remove", "rem", "rement");
  private static final Set<String> BROAD_OPERATION_SNAPSHOT_COMMANDS =
      Set.of("replacenear", "fixlava", "fixwater", "drain", "fill", "fillr", "stack");

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
    return OPERATION_SNAPSHOT_COMMANDS.contains(commandName(arguments));
  }

  static boolean isEntityRefreshCommand(String arguments) {
    return ENTITY_REFRESH_COMMANDS.contains(commandName(arguments));
  }

  static boolean isBroadOperationSnapshotCommand(String arguments) {
    return BROAD_OPERATION_SNAPSHOT_COMMANDS.contains(commandName(arguments));
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
