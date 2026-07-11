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
        Commands.literal("exort").executes(this::help);
    root.then(Commands.literal("help").executes(this::help));

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
    return Commands.literal(literal).executes(this::version);
  }

  private int help(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    Lang lang = lang();
    CommandFeedback.sendBlock(
        sender,
        Component.text(lang.tr(sender, "message.help_header")),
        rootHelpLines(lang, sender, hasAdminPermission(sender), hasGivePermission(sender)));
    return 1;
  }

  static List<Component> rootHelpLines(Lang lang, boolean admin) {
    return rootHelpLines(lang, null, admin, admin);
  }

  static List<Component> rootHelpLines(Lang lang, CommandSender sender, boolean admin) {
    return rootHelpLines(lang, sender, admin, admin);
  }

  static List<Component> rootHelpLines(
      Lang lang, CommandSender sender, boolean admin, boolean give) {
    List<Component> lines = new ArrayList<>();
    lines.add(helpLine(lang, sender, "/exort version", "message.help_version"));
    if (give) {
      lines.add(helpLine(lang, sender, "/exort inventory", "message.help_inventory"));
      lines.add(helpLine(lang, sender, "/exort give", "message.help_give"));
    }
    if (admin) {
      lines.add(helpLine(lang, sender, "/exort resourcepack", "message.help_resourcepack"));
      lines.add(helpLine(lang, sender, "/exort language", "message.help_language"));
      lines.add(helpLine(lang, sender, "/exort mode", "message.help_mode"));
      lines.add(helpLine(lang, sender, "/exort debug", "message.help_debug"));
      lines.add(helpLine(lang, sender, "/exort reload", "message.help_reload"));
    }
    return List.copyOf(lines);
  }

  private static Component helpLine(
      Lang lang, CommandSender sender, String command, String descriptionKey) {
    return CommandFeedback.commandLine(
        command,
        lang.tr(sender, descriptionKey),
        lang.tr(sender, "message.command_click", command));
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
              runSync(() -> sendMessage(sender, lang().tr(sender, "message.reload")));
            });
    return 1;
  }

  private int version(CommandContext<CommandSourceStack> context) {
    sendMessage(
        sender(context.getSource()),
        lang()
            .tr(
                sender(context.getSource()),
                "message.version",
                dependencies.pluginVersion().get()));
    return 1;
  }

  private void runSync(Runnable task) {
    PluginTasks.runSyncIfEnabled(plugin, task);
  }

  private void sendAsyncFailure(CommandSender sender, String action, Throwable err) {
    ExortLog.log(plugin, Level.WARNING, "Failed to " + action, err);
    runSync(() -> sendMessage(sender, lang().tr(sender, "message.operation_failed")));
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
    sendMessage(sender, lang().tr(sender, "message.no_permission"));
    return false;
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
