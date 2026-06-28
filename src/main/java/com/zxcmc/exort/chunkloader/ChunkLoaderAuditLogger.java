package com.zxcmc.exort.chunkloader;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ChunkLoaderAuditLogger implements AutoCloseable {
  private static final TextColor EXORT_ORANGE = TextColor.color(0xFF9900);
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  private final Logger logger;
  private final ChunkLoaderAuditConfig config;
  private final ChunkLoaderAuditFileWriter fileWriter;

  public ChunkLoaderAuditLogger(Logger logger, ChunkLoaderAuditConfig config) {
    this(logger, config, ChunkLoaderAuditFileWriter.noop());
  }

  public ChunkLoaderAuditLogger(
      Logger logger, ChunkLoaderAuditConfig config, ChunkLoaderAuditFileWriter fileWriter) {
    this.logger = logger;
    this.config =
        config == null
            ? new ChunkLoaderAuditConfig(true, EnumSet.allOf(ChunkLoaderAuditEvent.class))
            : config;
    this.fileWriter = fileWriter == null ? ChunkLoaderAuditFileWriter.noop() : fileWriter;
  }

  public void log(ChunkLoaderAuditEvent event, Player actor, UUID loaderId, Block block) {
    log(event, actor, loaderId, block, null);
  }

  public void log(
      ChunkLoaderAuditEvent event, Player actor, UUID loaderId, Block block, String detail) {
    if (event == null) {
      return;
    }
    int amount = amountFromDetail(detail);
    if (shouldConsoleLog(event)) {
      emitConsole(eventMessage(event, actor, loaderId, block, detail));
    }
    writeFile(
        fileAction(event),
        loaderId,
        amount,
        actor,
        event == ChunkLoaderAuditEvent.CLEANUP ? "Exort" : "Console",
        block,
        null,
        null,
        null,
        null,
        null,
        event == ChunkLoaderAuditEvent.CLEANUP ? detail : null);
  }

  public void logIssue(Player actor, Player target, int amount, String source) {
    int safeAmount = Math.max(1, amount);
    if (shouldConsoleLog(ChunkLoaderAuditEvent.ISSUE)) {
      boolean inventoryIssue =
          source != null && source.toLowerCase(Locale.ROOT).contains("inventory");
      Component message =
          prefix()
              .append(actor(actor))
              .append(
                  Component.text(inventoryIssue ? " received " : " issued ", NamedTextColor.GRAY))
              .append(chunkLoaderName(safeAmount, null));
      if (!inventoryIssue && target != null) {
        message =
            message.append(Component.text(" to ", NamedTextColor.GRAY)).append(playerName(target));
      }
      message = appendSource(message, source);
      message = appendActorLocation(message, actor);
      emitConsole(message);
    }
    writeFile(
        "issue",
        null,
        safeAmount,
        actor,
        "Console",
        null,
        null,
        null,
        null,
        source,
        target == null ? null : target.getName(),
        null);
  }

  public void logInventoryTransfer(
      Player actor, UUID loaderId, int amount, String destination, Location destinationLocation) {
    int safeAmount = Math.max(1, amount);
    if (shouldConsoleLog(ChunkLoaderAuditEvent.INVENTORY_MOVE)) {
      Component message =
          prefix()
              .append(actor(actor))
              .append(Component.text(" moved ", NamedTextColor.GRAY))
              .append(chunkLoaderName(safeAmount, loaderId))
              .append(Component.text(" into ", NamedTextColor.GRAY))
              .append(Component.text(cleanDestination(destination), NamedTextColor.YELLOW));
      emitConsole(appendActorLocation(message, actor));
    }
    writeFile(
        "inventory_into",
        loaderId,
        safeAmount,
        actor,
        "Console",
        null,
        null,
        actor == null ? null : actor.getLocation(),
        destinationLocation,
        "player_inventory",
        destination,
        null);
  }

  public void logInventoryTake(
      Player actor, UUID loaderId, int amount, String source, Location sourceLocation) {
    int safeAmount = Math.max(1, amount);
    if (shouldConsoleLog(ChunkLoaderAuditEvent.INVENTORY_MOVE)) {
      Component message =
          prefix()
              .append(actor(actor))
              .append(Component.text(" took ", NamedTextColor.GRAY))
              .append(chunkLoaderName(safeAmount, loaderId))
              .append(Component.text(" from ", NamedTextColor.GRAY))
              .append(Component.text(cleanDestination(source), NamedTextColor.YELLOW));
      emitConsole(appendActorLocation(message, actor));
    }
    writeFile(
        "inventory_take",
        loaderId,
        safeAmount,
        actor,
        "Console",
        null,
        null,
        sourceLocation,
        actor == null ? null : actor.getLocation(),
        source,
        "player_inventory",
        null);
  }

  public void logInventoryDisappearance(
      Player actor, UUID loaderId, int amount, String source, Location sourceLocation) {
    int safeAmount = Math.max(1, amount);
    if (shouldConsoleLog(ChunkLoaderAuditEvent.DESTROY)) {
      Component message =
          prefix()
              .append(chunkLoaderName(safeAmount, loaderId))
              .append(Component.text(" disappeared from ", NamedTextColor.GRAY))
              .append(Component.text(cleanDestination(source), NamedTextColor.YELLOW))
              .append(Component.text(" during inventory interaction", NamedTextColor.GRAY));
      if (sourceLocation != null && sourceLocation.getWorld() != null) {
        message =
            message
                .append(Component.text(" at ", NamedTextColor.GRAY))
                .append(locationCoordinates(sourceLocation))
                .append(Component.text(", ", NamedTextColor.GRAY))
                .append(worldName(sourceLocation.getWorld()));
      }
      emitConsole(appendActorLocation(message, actor));
    }
    writeFile(
        "disappeared",
        loaderId,
        safeAmount,
        actor,
        "Environment",
        null,
        sourceLocation,
        sourceLocation,
        null,
        source,
        null,
        "inventory_interaction");
  }

  public void logAutomationTransfer(
      UUID loaderId,
      int amount,
      String source,
      String destination,
      Location sourceLocation,
      Location destinationLocation) {
    int safeAmount = Math.max(1, amount);
    if (shouldConsoleLog(ChunkLoaderAuditEvent.INVENTORY_MOVE)) {
      Component message =
          prefix()
              .append(environment())
              .append(Component.text(" moved ", NamedTextColor.GRAY))
              .append(chunkLoaderName(safeAmount, loaderId))
              .append(Component.text(" from ", NamedTextColor.GRAY))
              .append(Component.text(cleanDestination(source), NamedTextColor.YELLOW))
              .append(Component.text(" to ", NamedTextColor.GRAY))
              .append(Component.text(cleanDestination(destination), NamedTextColor.YELLOW));
      emitConsole(message);
    }
    writeFile(
        "automation_move",
        loaderId,
        safeAmount,
        null,
        "Environment",
        null,
        destinationLocation,
        sourceLocation,
        destinationLocation,
        source,
        destination,
        null);
  }

  public void logAutomationLoss(
      UUID loaderId,
      int amount,
      String source,
      String destination,
      Location sourceLocation,
      Location destinationLocation) {
    int safeAmount = Math.max(1, amount);
    if (shouldConsoleLog(ChunkLoaderAuditEvent.DESTROY)) {
      Component message =
          prefix()
              .append(chunkLoaderName(safeAmount, loaderId))
              .append(
                  Component.text(" disappeared during automation transfer", NamedTextColor.GRAY))
              .append(Component.text(" from ", NamedTextColor.GRAY))
              .append(Component.text(cleanDestination(source), NamedTextColor.YELLOW))
              .append(Component.text(" to ", NamedTextColor.GRAY))
              .append(Component.text(cleanDestination(destination), NamedTextColor.YELLOW));
      emitConsole(message);
    }
    writeFile(
        "automation_lost",
        loaderId,
        safeAmount,
        null,
        "Environment",
        null,
        sourceLocation == null ? destinationLocation : sourceLocation,
        sourceLocation,
        destinationLocation,
        source,
        destination,
        "automation_transfer");
  }

  public void logFound(
      ChunkLoaderAuditEvent controllingEvent,
      Player actor,
      UUID loaderId,
      String source,
      Location location) {
    if (loaderId == null || controllingEvent == null) {
      return;
    }
    if (shouldConsoleLog(controllingEvent)) {
      Component message =
          prefix()
              .append(actor == null ? environment() : playerName(actor))
              .append(Component.text(" found ", NamedTextColor.GRAY))
              .append(chunkLoaderName(1, loaderId));
      message = appendSource(message, source);
      if (location != null && location.getWorld() != null) {
        message =
            message
                .append(Component.text(" at ", NamedTextColor.GRAY))
                .append(locationCoordinates(location))
                .append(Component.text(", ", NamedTextColor.GRAY))
                .append(worldName(location.getWorld()));
      }
      emitConsole(appendActorLocation(message, actor));
    }
    writeFile(
        "found", loaderId, 1, actor, "Environment", null, location, null, null, source, null, null);
  }

  public void logItemDestroy(
      Player actor, Player target, UUID loaderId, int amount, String reason, Location location) {
    int safeAmount = Math.max(1, amount);
    if (shouldConsoleLog(ChunkLoaderAuditEvent.DESTROY)) {
      Component message = itemLossStart(actor, target, loaderId, safeAmount, reason);
      if (target != null && (actor == null || !actor.getUniqueId().equals(target.getUniqueId()))) {
        message =
            message
                .append(Component.text(" from ", NamedTextColor.GRAY))
                .append(playerName(target));
      }
      if (reason != null && !reason.isBlank()) {
        message =
            message
                .append(Component.text(" via ", NamedTextColor.GRAY))
                .append(Component.text(humanReason(reason), NamedTextColor.YELLOW));
      }
      if (location != null && location.getWorld() != null) {
        message =
            message
                .append(Component.text(" at ", NamedTextColor.GRAY))
                .append(locationCoordinates(location))
                .append(Component.text(", ", NamedTextColor.GRAY))
                .append(worldName(location.getWorld()));
      }
      emitConsole(message);
    }
    writeFile(
        itemLossAction(reason),
        loaderId,
        safeAmount,
        actor,
        target == null ? "Environment" : "Console",
        null,
        location,
        null,
        null,
        null,
        target == null ? null : target.getName(),
        reason);
  }

  private boolean shouldConsoleLog(ChunkLoaderAuditEvent event) {
    return logger != null && config.shouldLog(event);
  }

  private void writeFile(
      String action,
      UUID loaderId,
      int amount,
      Player actor,
      String fallbackActor,
      Block block,
      Location eventLocation,
      Location sourceLocation,
      Location destinationLocation,
      String source,
      String destination,
      String reason) {
    if (!config.shouldWriteFile()) {
      return;
    }
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("timestamp", Instant.now().toString());
    fields.put("action", action == null || action.isBlank() ? "unknown" : action);
    fields.put("uuid", loaderId == null ? "unassigned" : loaderId.toString());
    fields.put("amount", Integer.toString(Math.max(1, amount)));
    fields.put("actor", actor == null ? fallback(fallbackActor, "Environment") : actor.getName());
    fields.put("actor_uuid", actor == null ? "-" : actor.getUniqueId().toString());
    fields.put("player_location", actor == null ? "-" : locationText(actor.getLocation()));
    fields.put("block_location", block == null ? "-" : blockLocationText(block));
    fields.put("event_location", locationText(eventLocation));
    fields.put("source", source);
    fields.put("source_location", locationText(sourceLocation));
    fields.put("destination", destination);
    fields.put("destination_location", locationText(destinationLocation));
    fields.put("reason", reason);
    fileWriter.write(formatFields(fields));
  }

  private static String formatFields(Map<String, String> fields) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> field : fields.entrySet()) {
      if (!first) {
        builder.append(' ');
      }
      first = false;
      builder.append(field.getKey()).append('=').append(fieldValue(field.getValue()));
    }
    return builder.toString();
  }

  private static String fieldValue(String value) {
    if (value == null || value.isBlank()) {
      return "-";
    }
    String sanitized = value.replace('\\', '/').replace('\n', ' ').replace('\r', ' ').trim();
    if (sanitized.matches("[A-Za-z0-9._:/@+\\-,]+")) {
      return sanitized;
    }
    return "\"" + sanitized.replace("\"", "\\\"") + "\"";
  }

  private Component eventMessage(
      ChunkLoaderAuditEvent event, Player actor, UUID loaderId, Block block, String detail) {
    Component message = prefix();
    if (event == ChunkLoaderAuditEvent.CLEANUP) {
      message = message.append(Component.text("Exort", NamedTextColor.GOLD));
    } else {
      message = message.append(actor(actor));
    }
    message =
        message
            .append(Component.text(" " + action(event) + " ", NamedTextColor.GRAY))
            .append(chunkLoaderName(amountFromDetail(detail), loaderId));
    if (block != null && block.getWorld() != null) {
      message =
          message
              .append(Component.text(" at ", NamedTextColor.GRAY))
              .append(blockCoordinates(block))
              .append(Component.text(", ", NamedTextColor.GRAY))
              .append(worldName(block.getWorld()));
    }
    if (event == ChunkLoaderAuditEvent.CLEANUP && detail != null && !detail.isBlank()) {
      message =
          message
              .append(Component.text(" (reason: ", NamedTextColor.DARK_GRAY))
              .append(Component.text(humanReason(detail), NamedTextColor.GRAY))
              .append(Component.text(")", NamedTextColor.DARK_GRAY));
    }
    return appendActorLocation(message, actor);
  }

  private static String action(ChunkLoaderAuditEvent event) {
    return switch (event) {
      case ISSUE -> "issued";
      case CRAFT -> "crafted";
      case INVENTORY_MOVE -> "moved";
      case DROP -> "dropped";
      case PICKUP -> "picked up";
      case PLACE -> "placed";
      case BREAK -> "broke";
      case DESTROY -> "lost";
      case CLEANUP -> "cleaned up";
    };
  }

  private static String fileAction(ChunkLoaderAuditEvent event) {
    return switch (event) {
      case ISSUE -> "issue";
      case CRAFT -> "craft";
      case INVENTORY_MOVE -> "inventory_move";
      case DROP -> "drop";
      case PICKUP -> "pickup";
      case PLACE -> "place";
      case BREAK -> "break";
      case DESTROY -> "lost";
      case CLEANUP -> "cleanup";
    };
  }

  private static Component prefix() {
    return Component.text("[Exort] ", EXORT_ORANGE);
  }

  private static Component actor(Player actor) {
    return actor == null ? Component.text("Console", NamedTextColor.GOLD) : playerName(actor);
  }

  private static Component environment() {
    return Component.text("Environment", NamedTextColor.GOLD);
  }

  private static Component destroyActor(Player actor, Player target) {
    if (actor != null) {
      return playerName(actor);
    }
    if (target != null) {
      return Component.text("Console", NamedTextColor.GOLD);
    }
    return environment();
  }

  private static Component itemLossStart(
      Player actor, Player target, UUID loaderId, int amount, String reason) {
    if (actor == null && target == null && !isKnownPhysicalDestruction(reason)) {
      return prefix().append(chunkLoaderName(amount, loaderId)).append(disappearedText());
    }
    return prefix()
        .append(destroyActor(actor, target))
        .append(Component.text(" " + itemLossAction(reason) + " ", NamedTextColor.GRAY))
        .append(chunkLoaderName(amount, loaderId));
  }

  private static Component disappearedText() {
    return Component.text(" disappeared", NamedTextColor.GRAY);
  }

  private static String itemLossAction(String reason) {
    String normalized = normalizedReason(reason);
    if ("/clear".equals(normalized) || "/exort inventory".equals(normalized)) {
      return "removed";
    }
    if (isKnownPhysicalDestruction(normalized)) {
      return "destroyed";
    }
    if ("inventory interaction".equals(normalized) || "automation transfer".equals(normalized)) {
      return "disappeared";
    }
    return "lost";
  }

  private static boolean isKnownPhysicalDestruction(String reason) {
    String normalized = normalizedReason(reason);
    return switch (normalized) {
      case "fire", "fire tick", "lava", "hot floor", "magma block", "void" -> true;
      default -> false;
    };
  }

  private static String normalizedReason(String reason) {
    return reason == null ? "" : humanReason(reason).toLowerCase(Locale.ROOT);
  }

  private static Component playerName(Player player) {
    return Component.text(player.getName(), NamedTextColor.GOLD)
        .append(Component.text(" (" + player.getUniqueId() + ")", NamedTextColor.DARK_GRAY));
  }

  private static Component chunkLoaderName(int amount, UUID loaderId) {
    Component component =
        amount > 1
            ? Component.text(amount + " Chunk Loaders", NamedTextColor.AQUA)
            : Component.text("Chunk Loader", NamedTextColor.AQUA);
    if (loaderId != null) {
      component =
          component
              .append(Component.text(" ", NamedTextColor.GRAY))
              .append(Component.text(loaderId.toString(), NamedTextColor.DARK_GRAY));
    }
    return component;
  }

  private static Component appendSource(Component message, String source) {
    if (source == null || source.isBlank()) {
      return message;
    }
    return message
        .append(Component.text(" via ", NamedTextColor.GRAY))
        .append(Component.text(source, NamedTextColor.YELLOW));
  }

  private static Component appendActorLocation(Component message, Player actor) {
    if (actor == null) {
      return message;
    }
    Location loc = actor.getLocation();
    World world = loc.getWorld();
    return message
        .append(Component.text(" (player at ", NamedTextColor.DARK_GRAY))
        .append(locationCoordinates(loc))
        .append(Component.text(", ", NamedTextColor.DARK_GRAY))
        .append(world == null ? Component.text("unknown", NamedTextColor.RED) : worldName(world))
        .append(Component.text(")", NamedTextColor.DARK_GRAY));
  }

  private static Component blockCoordinates(Block block) {
    return Component.text(
        block.getX() + " " + block.getY() + " " + block.getZ(), NamedTextColor.YELLOW);
  }

  private static Component locationCoordinates(Location location) {
    return Component.text(
        format(location.getX()) + " " + format(location.getY()) + " " + format(location.getZ()),
        NamedTextColor.YELLOW);
  }

  private static Component worldName(World world) {
    return Component.text(world.getName(), NamedTextColor.GREEN);
  }

  private static int amountFromDetail(String detail) {
    if (detail == null || !detail.startsWith("amount=")) {
      return 1;
    }
    try {
      return Math.max(1, Integer.parseInt(detail.substring("amount=".length()).trim()));
    } catch (NumberFormatException ignored) {
      return 1;
    }
  }

  private static String humanReason(String reason) {
    return reason == null ? "" : reason.replace('\n', ' ').replace('_', ' ').trim();
  }

  private static String cleanDestination(String destination) {
    if (destination == null || destination.isBlank()) {
      return "external inventory";
    }
    return destination.replace('_', ' ').toLowerCase(Locale.ROOT);
  }

  private void emitConsole(Component message) {
    if (message == null) {
      return;
    }
    if (isPrimaryThread()) {
      try {
        Bukkit.getConsoleSender().sendMessage(message);
        return;
      } catch (RuntimeException ignored) {
        // Fall back to the plain logger below.
      }
    }
    if (logger != null) {
      logger.info(PLAIN.serialize(message));
    }
  }

  private static boolean isPrimaryThread() {
    try {
      return Bukkit.isPrimaryThread();
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private static String locationText(Location location) {
    if (location == null || location.getWorld() == null) {
      return "-";
    }
    return location.getWorld().getName()
        + " "
        + format(location.getX())
        + " "
        + format(location.getY())
        + " "
        + format(location.getZ());
  }

  private static String blockLocationText(Block block) {
    if (block == null || block.getWorld() == null) {
      return "-";
    }
    return block.getWorld().getName()
        + " "
        + block.getX()
        + " "
        + block.getY()
        + " "
        + block.getZ();
  }

  private static String fallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  @Override
  public void close() {
    fileWriter.close();
  }
}
