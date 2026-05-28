package com.zxcmc.exort.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zxcmc.exort.feedback.CommandFeedback;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

final class PackCommand {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String ARG_PACK_TARGET = "packTarget";

  private final ExortBrigadierDependencies dependencies;

  PackCommand(ExortBrigadierDependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
  }

  LiteralArgumentBuilder<CommandSourceStack> build(String literal) {
    return Commands.literal(literal)
        .requires(source -> hasAdminPermission(sender(source)))
        .executes(this::usage)
        .then(Commands.literal("status").executes(this::status))
        .then(Commands.literal("rebuild").executes(this::rebuild))
        .then(
            Commands.literal("send")
                .executes(ctx -> send(ctx, "@self"))
                .then(Commands.literal("all").executes(ctx -> send(ctx, "all")))
                .then(
                    Commands.argument(ARG_PACK_TARGET, StringArgumentType.word())
                        .suggests(this::suggestTargets)
                        .executes(
                            ctx -> send(ctx, StringArgumentType.getString(ctx, ARG_PACK_TARGET)))));
  }

  private int usage(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandFeedback.sendBlock(
        sender(context.getSource()),
        Component.text(dependencies.lang().tr("message.usage_resourcepack_header")),
        List.of(
            usageLine("/exort resourcepack status", "message.usage_resourcepack_status"),
            usageLine("/exort resourcepack rebuild", "message.usage_resourcepack_rebuild"),
            usageLine("/exort resourcepack send [player|all]", "message.usage_resourcepack_send")));
    return 1;
  }

  private int status(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    var service = dependencies.resourcePackService().get();
    if (service == null) {
      sendMessage(
          sender,
          dependencies
              .lang()
              .tr(
                  "message.pack_unavailable",
                  dependencies.lang().tr("message.pack_service_not_started")));
      return 1;
    }
    List<String> lines = service.statusLines();
    if (!lines.isEmpty()) {
      CommandFeedback.sendBlock(sender, lines.get(0), lines.subList(1, lines.size()));
    }
    return 1;
  }

  private Component usageLine(String command, String descriptionKey) {
    return CommandFeedback.commandLine(
        command,
        dependencies.lang().tr(descriptionKey),
        dependencies.lang().tr("message.command_click", command));
  }

  private int rebuild(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    dependencies.resourcePackReloader().run();
    var service = dependencies.resourcePackService().get();
    var lines = new ArrayList<String>();
    lines.add(dependencies.lang().tr("message.pack_rebuilt"));
    if (service == null) {
      lines.add(
          dependencies
              .lang()
              .tr(
                  "message.pack_unavailable",
                  dependencies.lang().tr("message.pack_service_not_started")));
    } else {
      lines.addAll(service.statusLines());
    }
    CommandFeedback.sendBlock(sender, lines.getFirst(), lines.subList(1, lines.size()));
    return 1;
  }

  private int send(CommandContext<CommandSourceStack> context, String target) {
    if (!ensurePermission(context)) return 0;
    var service = dependencies.resourcePackService().get();
    CommandSender sender = sender(context.getSource());
    if (service == null || !service.dispatchReady()) {
      String reason =
          service == null
              ? dependencies.lang().tr("message.pack_service_not_started")
              : service.unavailableReason();
      sendMessage(sender, dependencies.lang().tr("message.pack_unavailable", reason));
      return 1;
    }
    if ("all".equalsIgnoreCase(target)) {
      int sent = service.sendAll();
      sendMessage(sender, dependencies.lang().tr("message.pack_sent_all", sent));
      return 1;
    }
    Player player;
    if ("@self".equals(target) && sender instanceof Player self) {
      player = self;
    } else {
      player = Bukkit.getPlayerExact(target);
    }
    if (player == null) {
      sendMessage(sender, dependencies.lang().tr("message.player_not_found"));
      return 1;
    }
    if (service.send(player)) {
      sendMessage(sender, dependencies.lang().tr("message.pack_sent", player.getName()));
    } else {
      sendMessage(
          sender, dependencies.lang().tr("message.pack_unavailable", service.unavailableReason()));
    }
    return 1;
  }

  private CompletableFuture<Suggestions> suggestTargets(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = new ArrayList<>();
    options.add("all");
    for (Player player : Bukkit.getOnlinePlayers()) {
      options.add(player.getName());
    }
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toLowerCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
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
