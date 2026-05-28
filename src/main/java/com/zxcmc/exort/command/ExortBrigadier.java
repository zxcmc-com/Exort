package com.zxcmc.exort.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExortBrigadier {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String PERMISSION_GIVE = "exort.storagenetwork.give";
  private final ExortBrigadierDependencies dependencies;
  private final JavaPlugin plugin;

  public ExortBrigadier(ExortBrigadierDependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    this.plugin = dependencies.plugin();
  }

  public LiteralCommandNode<CommandSourceStack> build() {
    LiteralArgumentBuilder<CommandSourceStack> root =
        Commands.literal("exort")
            .requires(source -> hasAnyPermission(sender(source)))
            .executes(this::help);
    root.then(new DebugCommand(dependencies).build());

    root.then(new GiveCommand(dependencies).build());

    root.then(
        Commands.literal("reload")
            .requires(source -> hasAdminPermission(sender(source)))
            .executes(this::reload));

    root.then(new LangCommand(dependencies).build());

    root.then(new ModeCommand(dependencies).build());

    root.then(new PackCommand(dependencies).build());

    root.then(
        Commands.literal("version")
            .requires(source -> hasAdminPermission(sender(source)))
            .executes(this::version));

    return root.build();
  }

  private int help(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (!hasAnyPermission(sender)) {
      sendMessage(sender, lang().tr("message.no_permission"));
      return 0;
    }
    Lang lang = lang();
    sendMessage(sender, lang.tr("message.help_header"));
    if (hasAdminPermission(sender)) {
      sendMessage(sender, lang.tr("message.help_debug", "exort"));
    }
    sendMessage(sender, lang.tr("message.help_give", "exort"));
    if (hasAdminPermission(sender)) {
      sendMessage(sender, lang.tr("message.help_reload", "exort"));
      sendMessage(sender, lang.tr("message.help_lang", "exort"));
      sendMessage(sender, lang.tr("message.help_mode", "exort"));
      sendMessage(sender, lang.tr("message.help_pack", "exort"));
      sendMessage(sender, lang.tr("message.help_version", "exort"));
    }
    return 1;
  }

  private int reload(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    dependencies
        .runtimeReloader()
        .get()
        .whenComplete(
            (status, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "reload runtime", err);
                return;
              }
              runSync(() -> sendMessage(sender, lang().tr("message.reload")));
            });
    return 1;
  }

  private int version(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sendMessage(
        sender(context.getSource()),
        lang().tr("message.version", dependencies.pluginVersion().get()));
    return 1;
  }

  private void runSync(Runnable task) {
    PluginTasks.runSyncIfEnabled(plugin, task);
  }

  private void sendAsyncFailure(CommandSender sender, String action, Throwable err) {
    ExortLog.log(plugin, Level.WARNING, "Failed to " + action, err);
    runSync(() -> sendMessage(sender, lang().tr("message.operation_failed")));
  }

  private Lang lang() {
    return dependencies.lang();
  }

  private void sendMessage(CommandSender sender, String message) {
    CommandFeedback.send(sender, message);
  }

  private boolean ensurePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (hasAdminPermission(sender)) return true;
    sendMessage(sender, lang().tr("message.no_permission"));
    return false;
  }

  private static boolean hasAnyPermission(CommandSender sender) {
    return hasGivePermission(sender);
  }

  private static boolean hasGivePermission(CommandSender sender) {
    return hasAdminPermission(sender) || sender.hasPermission(PERMISSION_GIVE);
  }

  private static boolean hasAdminPermission(CommandSender sender) {
    return sender.hasPermission(PERMISSION_ADMIN);
  }

  private static CommandSender sender(CommandSourceStack source) {
    return source.getSender();
  }
}
