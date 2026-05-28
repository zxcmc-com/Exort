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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
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
    root.then(
        Commands.literal("help")
            .requires(source -> hasAnyPermission(sender(source)))
            .executes(this::help));

    InventoryCommand inventoryCommand = new InventoryCommand(dependencies);
    root.then(inventoryCommand.build("inventory"));
    root.then(inventoryCommand.build("inv"));

    root.then(new GiveCommand(dependencies).build());

    PackCommand packCommand = new PackCommand(dependencies);
    root.then(packCommand.build("resourcepack"));
    root.then(packCommand.build("pack"));

    LangCommand langCommand = new LangCommand(dependencies);
    root.then(langCommand.build("language"));
    root.then(langCommand.build("lang"));

    root.then(new ModeCommand(dependencies).build());

    root.then(new DebugCommand(dependencies).build());

    root.then(versionCommand("version"));
    root.then(versionCommand("ver"));

    root.then(
        Commands.literal("reload")
            .requires(source -> hasAdminPermission(sender(source)))
            .executes(this::reload));

    return root.build();
  }

  private LiteralArgumentBuilder<CommandSourceStack> versionCommand(String literal) {
    return Commands.literal(literal)
        .requires(source -> hasAdminPermission(sender(source)))
        .executes(this::version);
  }

  private int help(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (!hasAnyPermission(sender)) {
      sendMessage(sender, lang().tr("message.no_permission"));
      return 0;
    }
    Lang lang = lang();
    CommandFeedback.sendBlock(
        sender,
        Component.text(lang.tr("message.help_header")),
        rootHelpLines(lang, hasAdminPermission(sender)));
    return 1;
  }

  static List<Component> rootHelpLines(Lang lang, boolean admin) {
    List<Component> lines = new ArrayList<>();
    lines.add(helpLine(lang, "/exort inventory", "message.help_inventory"));
    lines.add(helpLine(lang, "/exort give", "message.help_give"));
    if (admin) {
      lines.add(helpLine(lang, "/exort resourcepack", "message.help_resourcepack"));
      lines.add(helpLine(lang, "/exort language", "message.help_language"));
      lines.add(helpLine(lang, "/exort mode", "message.help_mode"));
      lines.add(helpLine(lang, "/exort debug", "message.help_debug"));
      lines.add(helpLine(lang, "/exort version", "message.help_version"));
      lines.add(helpLine(lang, "/exort reload", "message.help_reload"));
    }
    return List.copyOf(lines);
  }

  private static Component helpLine(Lang lang, String command, String descriptionKey) {
    return CommandFeedback.commandLine(
        command, lang.tr(descriptionKey), lang.tr("message.command_click", command));
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
