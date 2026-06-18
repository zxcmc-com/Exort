package com.zxcmc.exort.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.integration.chorusfix.ChorusfixInstaller;
import com.zxcmc.exort.platform.PaperChorusPlantUpdates;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

final class ModeCommand {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String ARG_MODE = "mode";
  private static final String MODE_FIX_RESOURCE_COMMAND = "/exort mode fix RESOURCE";

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
                .then(
                    Commands.argument(ARG_MODE, StringArgumentType.word())
                        .suggests(this::suggestFixModes)
                        .executes(this::fix)));
  }

  private int usage(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    CommandFeedback.sendBlock(
        sender,
        Component.text(dependencies.lang().tr(sender, "message.usage_mode_header")),
        List.of(
            usageLine(sender, "/exort mode info", "message.usage_mode_info"),
            usageLine(sender, "/exort mode set <VANILLA|RESOURCE>", "message.usage_mode_set"),
            usageLine(sender, MODE_FIX_RESOURCE_COMMAND, "message.usage_mode_fix")));
    return 1;
  }

  private int info(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String title =
        dependencies
            .lang()
            .tr(
                sender,
                "message.mode_info",
                dependencies.configuredMode().get(),
                dependencies.effectiveMode().get());
    if (dependencies.resourceWireCarrierFallback().getAsBoolean()) {
      CommandFeedback.sendBlock(
          sender,
          title,
          List.of(
              dependencies
                  .lang()
                  .tr(sender, "message.mode_carrier_warning", MODE_FIX_RESOURCE_COMMAND)));
    } else {
      sendMessage(sender, title);
    }
    return 1;
  }

  private Component usageLine(CommandSender sender, String command, String descriptionKey) {
    return CommandFeedback.commandLine(
        command,
        dependencies.lang().tr(sender, descriptionKey),
        dependencies.lang().tr(sender, "message.command_click", command));
  }

  private int set(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String raw = StringArgumentType.getString(context, ARG_MODE);
    String normalized = raw.toUpperCase(Locale.ROOT);
    if (!normalized.equals("VANILLA") && !normalized.equals("RESOURCE")) {
      sendMessage(sender, dependencies.lang().tr(sender, "message.mode_invalid", raw));
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
      sendMessage(sender, dependencies.lang().tr(sender, "message.mode_fix_not_resource", raw));
      return 1;
    }
    fixResourceMode(sender);
    return 1;
  }

  private void fixResourceMode(CommandSender sender) {
    PaperChorusPlantUpdates.FixResult result = dependencies.chorusPlantUpdateDisabler().get();
    if (result.state() == PaperChorusPlantUpdates.State.MISSING) {
      sendMessage(
          sender,
          dependencies
              .lang()
              .tr(sender, "message.mode_fix_paper_missing", result.file().getPath()));
      return;
    }
    if (result.state() == PaperChorusPlantUpdates.State.ERROR) {
      sendPaperFixError(sender, result.file().getPath(), result.accessDenied(), result.error());
      return;
    }
    installChorusfixAndFinishModeFix(sender, result);
  }

  private void sendPaperFixError(
      CommandSender sender, String paperConfigPath, boolean accessDenied, String reason) {
    if (accessDenied) {
      sendMessage(
          sender,
          dependencies
              .lang()
              .tr(
                  sender,
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
                sender,
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
                                sender,
                                "message.mode_set",
                                normalized,
                                dependencies.effectiveMode().get()));
                    if (dependencies.resourceWireCarrierFallback().getAsBoolean()) {
                      sendMessage(
                          sender,
                          dependencies
                              .lang()
                              .tr(
                                  sender,
                                  "message.mode_carrier_warning",
                                  MODE_FIX_RESOURCE_COMMAND));
                    }
                  });
            });
  }

  private void installChorusfixAndFinishModeFix(
      CommandSender sender, PaperChorusPlantUpdates.FixResult paperResult) {
    Optional<ChorusfixInstaller.LoadedPlugin> loadedPlugin =
        dependencies.loadedChorusfixPlugin().get();
    sendMessage(sender, dependencies.lang().tr(sender, "message.mode_fix_chorusfix_checking"));
    Bukkit.getScheduler()
        .runTaskAsynchronously(
            dependencies.plugin(),
            () -> {
              ChorusfixInstaller.InstallResult installResult;
              try {
                installResult = dependencies.chorusfixInstaller().apply(loadedPlugin);
              } catch (RuntimeException e) {
                installResult =
                    ChorusfixInstaller.InstallResult.failed(ExortLog.unwrap(e).toString());
              }
              ChorusfixInstaller.InstallResult result = installResult;
              runSync(() -> finishModeFix(sender, paperResult, result));
            });
  }

  private void finishModeFix(
      CommandSender sender,
      PaperChorusPlantUpdates.FixResult paperResult,
      ChorusfixInstaller.InstallResult installResult) {
    if (!installResult.success()) {
      sendMessage(
          sender,
          dependencies
              .lang()
              .tr(
                  sender,
                  "message.mode_fix_chorusfix_failed",
                  installResult.reason(),
                  ChorusfixInstaller.MANUAL_URL));
      if (!paperResult.restartRequired()) {
        setMode(sender, "RESOURCE");
      }
      return;
    }

    if (!paperResult.restartRequired() && !installResult.restartRequired()) {
      sendMessage(sender, chorusfixLine(sender, installResult));
      setMode(sender, "RESOURCE");
      return;
    }

    dependencies.configuredModeSaver().accept("RESOURCE");
    notifyModeFixRestart(sender, paperResult, installResult);
    scheduleRestartAfterModeFix();
  }

  private void notifyModeFixRestart(
      CommandSender sender,
      PaperChorusPlantUpdates.FixResult paperResult,
      ChorusfixInstaller.InstallResult installResult) {
    String paperLineKey =
        !paperResult.restartRequired()
            ? null
            : paperResult.changed()
                ? "message.mode_fix_paper_changed"
                : "message.mode_fix_paper_restart_required";
    sendBlockToSenderAndPlayers(sender, paperLineKey, paperResult.file().getPath(), installResult);
  }

  private void sendBlockToSenderAndPlayers(
      CommandSender sender,
      String paperLineKey,
      String paperConfigPath,
      ChorusfixInstaller.InstallResult installResult) {
    List<String> senderLines =
        modeFixRestartLines(sender, paperLineKey, paperConfigPath, installResult);
    CommandFeedback.sendBlock(
        sender, senderLines.getFirst(), senderLines.subList(1, senderLines.size()));
    Set<UUID> skippedPlayers = new HashSet<>();
    if (sender instanceof Player player) {
      skippedPlayers.add(player.getUniqueId());
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (skippedPlayers.contains(player.getUniqueId())) {
        continue;
      }
      List<String> lines =
          modeFixRestartLines(player, paperLineKey, paperConfigPath, installResult);
      CommandFeedback.sendBlock(player, lines.getFirst(), lines.subList(1, lines.size()));
    }
  }

  private List<String> modeFixRestartLines(
      CommandSender recipient,
      String paperLineKey,
      String paperConfigPath,
      ChorusfixInstaller.InstallResult installResult) {
    List<String> lines = new ArrayList<>();
    if (paperLineKey != null) {
      lines.add(
          dependencies
              .lang()
              .tr(recipient, paperLineKey, PaperChorusPlantUpdates.SETTING_PATH, paperConfigPath));
    }
    lines.add(chorusfixLine(recipient, installResult));
    lines.add(dependencies.lang().tr(recipient, "message.mode_fix_exort_changed"));
    lines.add(dependencies.lang().tr(recipient, "message.mode_fix_restart_scheduled"));
    return List.copyOf(lines);
  }

  private String chorusfixLine(
      CommandSender recipient, ChorusfixInstaller.InstallResult installResult) {
    return switch (installResult.outcome()) {
      case CURRENT ->
          dependencies
              .lang()
              .tr(recipient, "message.mode_fix_chorusfix_current", installResult.version());
      case PRESENT ->
          dependencies
              .lang()
              .tr(
                  recipient,
                  "message.mode_fix_chorusfix_present",
                  installResult.version(),
                  displayPath(installResult.target()));
      case INSTALLED ->
          dependencies
              .lang()
              .tr(
                  recipient,
                  "message.mode_fix_chorusfix_installed",
                  installResult.version(),
                  displayPath(installResult.target()));
      case UPDATED ->
          dependencies
              .lang()
              .tr(
                  recipient,
                  "message.mode_fix_chorusfix_updated",
                  installResult.version(),
                  displayPath(installResult.target()));
      case FAILED ->
          dependencies
              .lang()
              .tr(
                  recipient,
                  "message.mode_fix_chorusfix_failed",
                  installResult.reason(),
                  ChorusfixInstaller.MANUAL_URL);
    };
  }

  private static String displayPath(java.nio.file.Path path) {
    return path == null ? "" : path.toString();
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

  private CompletableFuture<Suggestions> suggestFixModes(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toUpperCase(Locale.ROOT),
            List.of("RESOURCE"),
            new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private void sendAsyncFailure(CommandSender sender, String action, Throwable err) {
    ExortLog.log(dependencies.plugin(), Level.SEVERE, "Failed to " + action, err);
    runSync(() -> sendMessage(sender, dependencies.lang().tr(sender, "message.operation_failed")));
  }

  private void runSync(Runnable action) {
    PluginTasks.runSyncIfEnabled(dependencies.plugin(), action);
  }

  private boolean ensurePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (hasAdminPermission(sender)) return true;
    sendMessage(sender, dependencies.lang().tr(sender, "message.no_permission"));
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
