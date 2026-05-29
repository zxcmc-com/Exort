package com.zxcmc.exort.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.debug.PickDebugService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.DisplayCullingService;
import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

final class DebugCommand {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String ARG_PLAYER = "player";
  private static final String ARG_STORAGE_ID = "storageId";
  private static final String ARG_PLAYERS = "players";
  private static final String ARG_SECONDS = "seconds";
  private static final String ARG_VERBOSE_MODE = "verboseMode";

  private final ExortBrigadierDependencies dependencies;
  private final DebugCacheStatusRenderer cacheStatusRenderer;

  DebugCommand(ExortBrigadierDependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    this.cacheStatusRenderer =
        new DebugCacheStatusRenderer(
            new DebugCacheStatusRendererDependencies(
                dependencies.plugin(),
                dependencies.lang(),
                dependencies.storageManager(),
                dependencies.keys(),
                dependencies.cacheIdleUnloadSeconds(),
                dependencies.wireLimit(),
                dependencies.wireHardCap(),
                dependencies.wireMaterial(),
                dependencies.storageCarrier()));
  }

  LiteralArgumentBuilder<CommandSourceStack> build() {
    LiteralArgumentBuilder<CommandSourceStack> cacheVerbose =
        Commands.literal("cache")
            .then(
                Commands.literal("start")
                    .executes(ctx -> cacheVerboseStart(ctx, null, null))
                    .then(
                        Commands.literal("storage")
                            .then(
                                Commands.argument(ARG_STORAGE_ID, StringArgumentType.word())
                                    .executes(
                                        ctx ->
                                            cacheVerboseStart(
                                                ctx,
                                                null,
                                                StringArgumentType.getString(
                                                    ctx, ARG_STORAGE_ID)))))
                    .then(
                        Commands.argument(ARG_VERBOSE_MODE, StringArgumentType.word())
                            .suggests(this::suggestVerboseModes)
                            .executes(
                                ctx ->
                                    cacheVerboseStart(
                                        ctx,
                                        StringArgumentType.getString(ctx, ARG_VERBOSE_MODE),
                                        null))
                            .then(
                                Commands.literal("storage")
                                    .then(
                                        Commands.argument(ARG_STORAGE_ID, StringArgumentType.word())
                                            .executes(
                                                ctx ->
                                                    cacheVerboseStart(
                                                        ctx,
                                                        StringArgumentType.getString(
                                                            ctx, ARG_VERBOSE_MODE),
                                                        StringArgumentType.getString(
                                                            ctx, ARG_STORAGE_ID)))))))
            .then(Commands.literal("stop").executes(this::cacheVerboseStop));

    LiteralArgumentBuilder<CommandSourceStack> worldEditVerbose =
        Commands.literal("worldedit")
            .then(
                Commands.literal("start")
                    .executes(ctx -> worldEditVerboseStart(ctx, null))
                    .then(
                        Commands.argument(ARG_VERBOSE_MODE, StringArgumentType.word())
                            .suggests(this::suggestVerboseModes)
                            .executes(
                                ctx ->
                                    worldEditVerboseStart(
                                        ctx, StringArgumentType.getString(ctx, ARG_VERBOSE_MODE)))))
            .then(Commands.literal("stop").executes(this::worldEditVerboseStop));

    LiteralArgumentBuilder<CommandSourceStack> pickVerbose =
        Commands.literal("pick")
            .then(
                Commands.literal("start")
                    .executes(ctx -> pickVerboseStart(ctx, null))
                    .then(
                        Commands.argument(ARG_VERBOSE_MODE, StringArgumentType.word())
                            .suggests(this::suggestVerboseModes)
                            .executes(
                                ctx ->
                                    pickVerboseStart(
                                        ctx, StringArgumentType.getString(ctx, ARG_VERBOSE_MODE)))))
            .then(Commands.literal("stop").executes(this::pickVerboseStop));

    LiteralArgumentBuilder<CommandSourceStack> cullingVerbose =
        Commands.literal("culling")
            .then(
                Commands.literal("start")
                    .executes(ctx -> cullingVerboseStart(ctx, null))
                    .then(
                        Commands.argument(ARG_VERBOSE_MODE, StringArgumentType.word())
                            .suggests(this::suggestVerboseModes)
                            .executes(
                                ctx ->
                                    cullingVerboseStart(
                                        ctx, StringArgumentType.getString(ctx, ARG_VERBOSE_MODE)))))
            .then(Commands.literal("stop").executes(this::cullingVerboseStop));

    LiteralArgumentBuilder<CommandSourceStack> verbose =
        Commands.literal("verbose")
            .then(cacheVerbose)
            .then(worldEditVerbose)
            .then(pickVerbose)
            .then(cullingVerbose);

    return Commands.literal("debug")
        .requires(source -> hasAdminPermission(sender(source)))
        .executes(this::usage)
        .then(
            Commands.literal("culling")
                .then(
                    Commands.literal("client")
                        .then(
                            Commands.argument(ARG_PLAYER, StringArgumentType.word())
                                .suggests(this::suggestPlayers)
                                .then(
                                    Commands.literal("status")
                                        .executes(ctx -> cullingClient(ctx, "status")))
                                .then(
                                    Commands.literal("enable")
                                        .executes(ctx -> cullingClient(ctx, "enable")))
                                .then(
                                    Commands.literal("disable")
                                        .executes(ctx -> cullingClient(ctx, "disable"))))))
        .then(
            Commands.literal("player")
                .then(
                    Commands.argument(ARG_PLAYER, StringArgumentType.word())
                        .suggests(this::suggestPlayers)
                        .executes(this::debugPlayer)))
        .then(verbose)
        .then(
            Commands.literal("cache")
                .then(
                    Commands.literal("status")
                        .then(
                            Commands.argument(ARG_STORAGE_ID, StringArgumentType.word())
                                .suggests(this::suggestPlayers)
                                .executes(this::cacheStatus))))
        .then(
            Commands.literal("storage")
                .then(
                    Commands.argument(ARG_STORAGE_ID, StringArgumentType.word())
                        .suggests(this::suggestPlayers)
                        .executes(ctx -> debugStorage(ctx, false))
                        .then(Commands.literal("write").executes(ctx -> debugStorage(ctx, true)))))
        .then(
            Commands.literal("benchmark")
                .then(
                    Commands.literal("start")
                        .executes(ctx -> loadTestStart(ctx, -1, 0))
                        .then(
                            Commands.argument(ARG_PLAYERS, IntegerArgumentType.integer(1))
                                .executes(
                                    ctx ->
                                        loadTestStart(
                                            ctx,
                                            IntegerArgumentType.getInteger(ctx, ARG_PLAYERS),
                                            0))
                                .then(
                                    Commands.argument(ARG_SECONDS, IntegerArgumentType.integer(1))
                                        .executes(
                                            ctx ->
                                                loadTestStart(
                                                    ctx,
                                                    IntegerArgumentType.getInteger(
                                                        ctx, ARG_PLAYERS),
                                                    IntegerArgumentType.getInteger(
                                                        ctx, ARG_SECONDS))))))
                .then(Commands.literal("stop").executes(this::loadTestStop)));
  }

  private int cacheStatus(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String raw = StringArgumentType.getString(context, ARG_STORAGE_ID);
    String storageId = null;
    try {
      storageId = UUID.fromString(raw).toString();
    } catch (IllegalArgumentException ignored) {
      storageId = null;
    }
    if (storageId != null) {
      String finalStorageId = storageId;
      runSync(() -> cacheStatusRenderer.send(sender, finalStorageId));
      return 1;
    }
    Player online = Bukkit.getPlayerExact(raw);
    OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayer(raw);
    if (offline == null || (offline.getName() == null && !offline.hasPlayedBefore())) {
      sendMessage(sender, lang().tr("message.debug_storage_invalid"));
      return 1;
    }
    String playerName = offline.getName() != null ? offline.getName() : raw;
    dependencies
        .database()
        .getPlayerLastStorage(offline.getUniqueId())
        .whenComplete(
            (result, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "read player storage history", err);
                return;
              }
              runSync(
                  () -> {
                    if (result.isEmpty()) {
                      sendMessage(sender, lang().tr("message.debug_player_none", playerName));
                      return;
                    }
                    cacheStatusRenderer.send(sender, result.get().storageId());
                  });
            });
    return 1;
  }

  private int usage(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandFeedback.sendBlock(
        sender(context.getSource()),
        Component.text(lang().tr("message.usage_debug_header")),
        List.of(
            usageLine("/exort debug player <player>", "message.usage_debug_player"),
            usageLine(
                "/exort debug storage <storageId|player> [write]", "message.usage_debug_storage"),
            usageLine("/exort debug cache status <storageId|player>", "message.usage_debug_cache"),
            usageLine(
                "/exort debug verbose <cache|worldedit|pick|culling> start|stop [mode]",
                "message.usage_debug_verbose"),
            usageLine(
                "/exort debug culling client <player> status|enable|disable",
                "message.usage_debug_culling_client"),
            usageLine(
                "/exort debug benchmark start|stop [players] [seconds]",
                "message.usage_debug_benchmark")));
    return 1;
  }

  private Component usageLine(String command, String descriptionKey) {
    return CommandFeedback.commandLine(
        command, lang().tr(descriptionKey), lang().tr("message.command_click", command));
  }

  private int cacheVerboseStart(
      CommandContext<CommandSourceStack> context, String rawMode, String rawStorage) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    var mode = CacheDebugService.Mode.fromString(rawMode);
    if (rawMode != null && mode == null) {
      sendMessage(sender, lang().tr("message.debug_cache_mode_invalid", rawMode));
      return 0;
    }
    UUID filter = null;
    if (rawStorage != null && !rawStorage.isBlank()) {
      try {
        filter = UUID.fromString(rawStorage.trim());
      } catch (IllegalArgumentException e) {
        sendMessage(sender, lang().tr("message.debug_cache_storage_invalid", rawStorage));
        return 0;
      }
    }
    dependencies.cacheDebugService().start(sender, mode, filter);
    String modeName =
        (mode == null ? dependencies.cacheDebugService().getMode() : mode)
            .name()
            .toLowerCase(Locale.ROOT);
    String filterText =
        filter == null ? lang().tr("message.debug_cache_filter_none") : filter.toString();
    sendMessage(sender, lang().tr("message.debug_cache_started", modeName, filterText));
    return 1;
  }

  private int cacheVerboseStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    dependencies.cacheDebugService().stop(sender);
    sendMessage(sender, lang().tr("message.debug_cache_stopped"));
    return 1;
  }

  private int worldEditVerboseStart(CommandContext<CommandSourceStack> context, String rawMode) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    var mode = WorldEditDebugService.Mode.fromString(rawMode);
    if (rawMode != null && mode == null) {
      sendMessage(sender, lang().tr("message.debug_worldedit_mode_invalid", rawMode));
      return 0;
    }
    dependencies.worldEditDebugService().start(sender, mode);
    String modeName =
        (mode == null ? dependencies.worldEditDebugService().getMode() : mode)
            .name()
            .toLowerCase(Locale.ROOT);
    sendMessage(sender, lang().tr("message.debug_worldedit_started", modeName));
    return 1;
  }

  private int worldEditVerboseStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    dependencies.worldEditDebugService().stop(sender);
    sendMessage(sender, lang().tr("message.debug_worldedit_stopped"));
    return 1;
  }

  private int pickVerboseStart(CommandContext<CommandSourceStack> context, String rawMode) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    var mode = PickDebugService.Mode.fromString(rawMode);
    if (rawMode != null && mode == null) {
      sendMessage(sender, lang().tr("message.debug_pick_mode_invalid", rawMode));
      return 0;
    }
    dependencies.pickDebugService().start(sender, mode);
    String modeName =
        (mode == null ? dependencies.pickDebugService().getMode() : mode)
            .name()
            .toLowerCase(Locale.ROOT);
    sendMessage(sender, lang().tr("message.debug_pick_started", modeName));
    return 1;
  }

  private int pickVerboseStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    dependencies.pickDebugService().stop(sender);
    sendMessage(sender, lang().tr("message.debug_pick_stopped"));
    return 1;
  }

  private int cullingVerboseStart(CommandContext<CommandSourceStack> context, String rawMode) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    DisplayCullingService service = dependencies.displayCullingService().get();
    if (service == null) {
      sendMessage(sender, lang().tr("message.debug_culling_unavailable"));
      return 1;
    }
    var mode = DisplayCullingService.DebugMode.fromString(rawMode);
    if (rawMode != null && mode == null) {
      sendMessage(sender, lang().tr("message.debug_culling_mode_invalid", rawMode));
      return 0;
    }
    service.startDebug(sender, mode);
    String modeName =
        (mode == null ? service.getDebugMode() : mode).name().toLowerCase(Locale.ROOT);
    sendMessage(sender, lang().tr("message.debug_culling_started", modeName));
    return 1;
  }

  private int cullingVerboseStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    DisplayCullingService service = dependencies.displayCullingService().get();
    if (service == null) {
      sendMessage(sender, lang().tr("message.debug_culling_unavailable"));
      return 1;
    }
    service.stopDebug(sender);
    sendMessage(sender, lang().tr("message.debug_culling_stopped"));
    return 1;
  }

  private int cullingClient(CommandContext<CommandSourceStack> context, String action) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    DisplayCullingService service = dependencies.displayCullingService().get();
    if (service == null) {
      sendMessage(sender, lang().tr("message.debug_culling_unavailable"));
      return 1;
    }
    String rawPlayer = StringArgumentType.getString(context, ARG_PLAYER);
    OfflinePlayer target = resolveOfflinePlayer(rawPlayer);
    if (target == null) {
      sendMessage(sender, lang().tr("message.debug_culling_client_invalid", rawPlayer));
      return 0;
    }
    String targetName =
        target.getName() == null ? target.getUniqueId().toString() : target.getName();
    DisplayCullingService.ClientCullingBypassStatus status =
        switch (action) {
          case "enable" -> service.setClientCullingBypass(target.getUniqueId(), true);
          case "disable" -> service.setClientCullingBypass(target.getUniqueId(), false);
          default -> service.clientCullingBypassStatus(target.getUniqueId());
        };
    sendMessage(
        sender,
        lang()
            .tr(
                "message.debug_culling_client_status",
                targetName,
                status.playerListed() ? "enabled" : "disabled",
                status.autoDetected() ? "detected" : "not-detected",
                status.source(),
                status.active() ? "active" : "inactive",
                status.configEnabled() ? "enabled" : "disabled",
                status.probeStatus().summary()));
    return 1;
  }

  private int loadTestStart(
      CommandContext<CommandSourceStack> context, int players, int durationSeconds) {
    if (!ensurePermission(context)) return 0;
    dependencies.loadTestService().start(sender(context.getSource()), players, durationSeconds);
    return 1;
  }

  private int loadTestStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    dependencies.loadTestService().stop(true);
    return 1;
  }

  private int debugPlayer(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    String name = StringArgumentType.getString(context, ARG_PLAYER);
    CommandSender sender = sender(context.getSource());
    Player online = Bukkit.getPlayerExact(name);
    if (online != null) {
      GuiSession session = dependencies.sessionManager().sessionFor(online);
      if (session != null && session.getStorageLocation() != null) {
        var loc = session.getStorageLocation();
        String storageId = session.getStorageId();
        sendMessage(
            sender,
            clickableDebugPlayer(
                lang()
                    .tr(
                        "message.debug_player_active",
                        online.getName(),
                        storageId,
                        session.getTier().key(),
                        loc.getWorld().getName(),
                        loc.getBlockX(),
                        loc.getBlockY(),
                        loc.getBlockZ()),
                storageId));
        return 1;
      }
    }

    var offline = Bukkit.getOfflinePlayer(name);
    if (offline == null || offline.getUniqueId() == null) {
      sendMessage(sender, lang().tr("message.player_not_found"));
      return 1;
    }
    String playerName = offline.getName() != null ? offline.getName() : name;
    dependencies
        .database()
        .getPlayerLastStorage(offline.getUniqueId())
        .whenComplete(
            (result, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "read player storage history", err);
                return;
              }
              runSync(
                  () -> {
                    if (result.isEmpty()) {
                      sendMessage(sender, lang().tr("message.debug_player_none", playerName));
                      return;
                    }
                    var data = result.get();
                    sendMessage(
                        sender,
                        clickableDebugPlayer(
                            lang()
                                .tr(
                                    "message.debug_player_last",
                                    playerName,
                                    data.storageId(),
                                    data.tier(),
                                    data.world(),
                                    data.x(),
                                    data.y(),
                                    data.z()),
                            data.storageId()));
                  });
            });
    return 1;
  }

  private int debugStorage(CommandContext<CommandSourceStack> context, boolean write) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    if (!(sender instanceof Player player)) {
      sendMessage(sender, lang().tr("message.only_player"));
      return 1;
    }
    String raw = StringArgumentType.getString(context, ARG_STORAGE_ID);
    try {
      UUID.fromString(raw);
      openStorageById(raw, write, player, sender);
    } catch (IllegalArgumentException ex) {
      Player online = Bukkit.getPlayerExact(raw);
      OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayer(raw);
      if (offline == null || (offline.getName() == null && !offline.hasPlayedBefore())) {
        sendMessage(sender, lang().tr("message.debug_storage_invalid"));
        return 1;
      }
      String playerName = offline.getName() != null ? offline.getName() : raw;
      dependencies
          .database()
          .getPlayerLastStorage(offline.getUniqueId())
          .whenComplete(
              (result, err) -> {
                if (err != null) {
                  sendAsyncFailure(sender, "read player storage history", err);
                  return;
                }
                runSync(
                    () -> {
                      if (result.isEmpty()) {
                        sendMessage(sender, lang().tr("message.debug_player_none", playerName));
                        return;
                      }
                      openStorageById(result.get().storageId(), write, player, sender);
                    });
              });
    }
    return 1;
  }

  private void openStorageById(
      String storageId, boolean write, Player viewer, CommandSender feedback) {
    dependencies
        .database()
        .getStorageTier(storageId)
        .thenCompose(
            optTier -> {
              if (optTier.isEmpty()) {
                return CompletableFuture.completedFuture(new DebugStorageOpen(optTier, null));
              }
              return dependencies
                  .storageManager()
                  .getOrLoad(storageId)
                  .thenApply(cache -> new DebugStorageOpen(optTier, cache));
            })
        .whenComplete(
            (open, err) -> {
              if (err != null) {
                sendAsyncFailure(feedback, "open debug storage " + storageId, err);
                return;
              }
              runSync(
                  () -> {
                    if (open.optTier().isEmpty() || open.cache() == null) {
                      sendMessage(feedback, lang().tr("message.debug_storage_missing", storageId));
                      return;
                    }
                    StorageTier tier = StorageTier.fromString(open.optTier().get()).orElse(null);
                    if (tier == null) {
                      sendMessage(feedback, lang().tr("message.debug_storage_missing", storageId));
                      return;
                    }
                    if (!viewer.isOnline()) return;
                    dependencies
                        .sessionManager()
                        .openDebugSession(viewer, open.cache(), tier, write);
                    sendMessage(
                        feedback,
                        lang()
                            .tr(
                                "message.debug_storage_opened",
                                storageId,
                                write
                                    ? lang().tr("debug.mode.write")
                                    : lang().tr("debug.mode.read")));
                  });
            });
  }

  private void runSync(Runnable task) {
    PluginTasks.runSyncIfEnabled(dependencies.plugin(), task);
  }

  private void sendAsyncFailure(CommandSender sender, String action, Throwable err) {
    ExortLog.log(dependencies.plugin(), Level.WARNING, "Failed to " + action, err);
    runSync(() -> sendMessage(sender, lang().tr("message.operation_failed")));
  }

  private Lang lang() {
    return dependencies.lang();
  }

  private void sendMessage(CommandSender sender, String message) {
    CommandFeedback.send(sender, message);
  }

  private void sendMessage(CommandSender sender, Component message) {
    CommandFeedback.send(sender, message);
  }

  private record DebugStorageOpen(Optional<String> optTier, StorageCache cache) {}

  private Component clickableDebugPlayer(String message, String storageId) {
    int idx = message.indexOf(storageId);
    if (idx < 0) {
      return Component.text(message);
    }
    Component before = Component.text(message.substring(0, idx));
    Component idComponent =
        Component.text(storageId)
            .clickEvent(ClickEvent.suggestCommand("/exort debug storage " + storageId))
            .hoverEvent(
                net.kyori.adventure.text.event.HoverEvent.showText(
                    Component.text(lang().tr("message.debug_player_click", storageId))));
    Component after = Component.text(message.substring(idx + storageId.length()));
    return before.append(idComponent).append(after);
  }

  private CompletableFuture<Suggestions> suggestPlayers(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      options.add(player.getName());
    }
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toLowerCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private OfflinePlayer resolveOfflinePlayer(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    Player online = Bukkit.getPlayerExact(raw);
    if (online != null) {
      return online;
    }
    try {
      return Bukkit.getOfflinePlayer(UUID.fromString(raw.trim()));
    } catch (IllegalArgumentException ignored) {
      OfflinePlayer offline = Bukkit.getOfflinePlayer(raw.trim());
      if (offline.hasPlayedBefore() || offline.getName() != null) {
        return offline;
      }
      return null;
    }
  }

  private CompletableFuture<Suggestions> suggestVerboseModes(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = List.of("compact", "normal", "full");
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toLowerCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private boolean ensurePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (hasAdminPermission(sender)) return true;
    sendMessage(sender, lang().tr("message.no_permission"));
    return false;
  }

  private static boolean hasAdminPermission(CommandSender sender) {
    return sender.hasPermission(PERMISSION_ADMIN);
  }

  private static CommandSender sender(CommandSourceStack source) {
    return source.getSender();
  }
}
