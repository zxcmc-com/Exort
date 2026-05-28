package com.zxcmc.exort.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.platform.PaperChorusPlantUpdates;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

final class ModeCommand {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String ARG_MODE = "mode";

  private final ExortBrigadierDependencies dependencies;

  ModeCommand(ExortBrigadierDependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
  }

  LiteralArgumentBuilder<CommandSourceStack> build() {
    return Commands.literal("mode")
        .requires(source -> hasAdminPermission(sender(source)))
        .executes(this::usage)
        .then(Commands.literal("info").executes(this::info))
        .then(
            Commands.literal("set")
                .then(
                    Commands.argument(ARG_MODE, StringArgumentType.word())
                        .suggests(this::suggestModes)
                        .executes(this::set)))
        .then(
            Commands.literal("fix")
                .executes(this::usage)
                .then(
                    Commands.argument(ARG_MODE, StringArgumentType.word())
                        .suggests(this::suggestResourceMode)
                        .executes(this::fix)));
  }

  private int usage(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sendMessage(sender(context.getSource()), dependencies.lang().tr("message.usage_mode"));
    return 1;
  }

  private int info(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    sendMessage(
        sender,
        dependencies
            .lang()
            .tr(
                "message.mode_info",
                dependencies.configuredMode().get(),
                dependencies.effectiveMode().get()));
    if (!dependencies.modeFallbackReason().get().isBlank()) {
      sendMessage(
          sender,
          dependencies.lang().tr("message.mode_fallback", dependencies.modeFallbackReason().get()));
    }
    return 1;
  }

  private int set(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String raw = StringArgumentType.getString(context, ARG_MODE);
    String normalized = raw.toUpperCase(Locale.ROOT);
    if (!normalized.equals("VANILLA") && !normalized.equals("RESOURCE")) {
      sendMessage(sender, dependencies.lang().tr("message.mode_invalid", raw));
      return 1;
    }
    setMode(sender, normalized);
    return 1;
  }

  private int fix(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String raw = StringArgumentType.getString(context, ARG_MODE);
    String normalized = raw.toUpperCase(Locale.ROOT);
    if (!normalized.equals("RESOURCE")) {
      sendMessage(sender, dependencies.lang().tr("message.mode_fix_resource_only"));
      return 1;
    }

    PaperChorusPlantUpdates.Status status = dependencies.chorusPlantUpdateStatus().get();
    if (status.state() == PaperChorusPlantUpdates.State.MISSING) {
      sendMessage(
          sender,
          dependencies.lang().tr("message.mode_fix_paper_missing", status.file().getPath()));
      return 1;
    }
    if (status.state() == PaperChorusPlantUpdates.State.ERROR) {
      sendPaperFixError(sender, status.file().getPath(), status.accessDenied(), status.error());
      return 1;
    }
    if (status.disabled()) {
      setMode(sender, "RESOURCE");
      return 1;
    }

    PaperChorusPlantUpdates.FixResult result = dependencies.chorusPlantUpdateDisabler().get();
    if (result.state() == PaperChorusPlantUpdates.State.MISSING) {
      sendMessage(
          sender,
          dependencies.lang().tr("message.mode_fix_paper_missing", result.file().getPath()));
      return 1;
    }
    if (result.state() == PaperChorusPlantUpdates.State.ERROR) {
      sendPaperFixError(sender, result.file().getPath(), result.accessDenied(), result.error());
      return 1;
    }
    if (!result.changed()) {
      setMode(sender, "RESOURCE");
      return 1;
    }

    dependencies.configuredModeSaver().accept("RESOURCE");
    notifyModeFixRestart(sender, result.file().getPath());
    scheduleRestartAfterModeFix();
    return 1;
  }

  private void sendPaperFixError(
      CommandSender sender, String paperConfigPath, boolean accessDenied, String reason) {
    if (accessDenied) {
      sendMessage(
          sender,
          dependencies
              .lang()
              .tr(
                  "message.mode_fix_paper_access_denied",
                  paperConfigPath,
                  PaperChorusPlantUpdates.SETTING_PATH));
      return;
    }
    sendMessage(
        sender,
        dependencies
            .lang()
            .tr(
                "message.mode_fix_paper_error",
                paperConfigPath,
                reason,
                PaperChorusPlantUpdates.SETTING_PATH));
  }

  private void setMode(CommandSender sender, String normalized) {
    dependencies.configuredModeSaver().accept(normalized);
    dependencies
        .runtimeReloader()
        .get()
        .whenComplete(
            (status, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "set mode", err);
                return;
              }
              runSync(
                  () -> {
                    sendMessage(
                        sender,
                        dependencies
                            .lang()
                            .tr(
                                "message.mode_set",
                                normalized,
                                dependencies.effectiveMode().get()));
                    if (!dependencies.modeFallbackReason().get().isBlank()) {
                      sendMessage(
                          sender,
                          dependencies
                              .lang()
                              .tr(
                                  "message.mode_fallback",
                                  dependencies.modeFallbackReason().get()));
                    }
                  });
            });
  }

  private void notifyModeFixRestart(CommandSender sender, String paperConfigPath) {
    sendToSenderAndPlayers(
        sender,
        dependencies
            .lang()
            .tr(
                "message.mode_fix_paper_changed",
                PaperChorusPlantUpdates.SETTING_PATH,
                paperConfigPath));
    sendToSenderAndPlayers(sender, dependencies.lang().tr("message.mode_fix_exort_changed"));
    sendToSenderAndPlayers(sender, dependencies.lang().tr("message.mode_fix_restart_scheduled"));
  }

  private void sendToSenderAndPlayers(CommandSender sender, String message) {
    sendMessage(sender, message);
    Set<UUID> skippedPlayers = new HashSet<>();
    if (sender instanceof Player player) {
      skippedPlayers.add(player.getUniqueId());
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (skippedPlayers.contains(player.getUniqueId())) {
        continue;
      }
      sendMessage(player, message);
    }
  }

  private void scheduleRestartAfterModeFix() {
    Bukkit.getScheduler().runTaskLater(dependencies.plugin(), this::restartWithStopFallback, 200L);
  }

  private void restartWithStopFallback() {
    if (!dependencies.plugin().isEnabled()) {
      return;
    }
    boolean restarted = false;
    try {
      restarted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
    } catch (RuntimeException e) {
      ExortLog.log(
          dependencies.plugin(),
          Level.WARNING,
          "Failed to dispatch restart; falling back to stop.",
          e);
    }
    if (restarted) {
      return;
    }
    ExortLog.warn("Restart command was unavailable or failed; falling back to stop.");
    try {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
    } catch (RuntimeException e) {
      ExortLog.log(
          dependencies.plugin(),
          Level.SEVERE,
          "Failed to dispatch stop after restart fallback.",
          e);
    }
  }

  private CompletableFuture<Suggestions> suggestModes(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = List.of("VANILLA", "RESOURCE");
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toUpperCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestResourceMode(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = List.of("RESOURCE");
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toUpperCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private void sendAsyncFailure(CommandSender sender, String action, Throwable err) {
    ExortLog.log(dependencies.plugin(), Level.SEVERE, "Failed to " + action, err);
    runSync(() -> sendMessage(sender, dependencies.lang().tr("message.operation_failed")));
  }

  private void runSync(Runnable action) {
    PluginTasks.runSyncIfEnabled(dependencies.plugin(), action);
  }

  private boolean ensurePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (hasAdminPermission(sender)) return true;
    sendMessage(sender, dependencies.lang().tr("message.no_permission"));
    return false;
  }

  private static boolean hasAdminPermission(CommandSender sender) {
    return sender.hasPermission(PERMISSION_ADMIN);
  }

  private static CommandSender sender(CommandSourceStack source) {
    return source.getSender();
  }

  private static void sendMessage(CommandSender sender, String message) {
    CommandFeedback.send(sender, message);
  }
}
