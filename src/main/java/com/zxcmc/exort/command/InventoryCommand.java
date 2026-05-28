package com.zxcmc.exort.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.infra.logging.ExortLog;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class InventoryCommand {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String PERMISSION_GIVE = "exort.storagenetwork.give";

  private final ExortBrigadierDependencies dependencies;

  InventoryCommand(ExortBrigadierDependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
  }

  LiteralArgumentBuilder<CommandSourceStack> build(String literal) {
    return Commands.literal(literal)
        .requires(source -> hasGivePermission(sender(source)))
        .executes(this::openInventory);
  }

  private int openInventory(CommandContext<CommandSourceStack> context) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    if (!(sender instanceof Player player)) {
      sendMessage(sender, dependencies.lang().tr("message.only_player"));
      return 1;
    }
    try {
      new ExortGiveMenu(dependencies.customItems(), () -> dependencies.wirelessService().create())
          .open(player);
    } catch (IllegalStateException e) {
      ExortLog.log(dependencies.plugin(), Level.WARNING, "Failed to open Exort inventory menu", e);
      sendMessage(sender, dependencies.lang().tr("message.operation_failed"));
    }
    return 1;
  }

  private boolean ensureGivePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (hasGivePermission(sender)) return true;
    sendMessage(sender, dependencies.lang().tr("message.no_permission"));
    return false;
  }

  private static boolean hasGivePermission(CommandSender sender) {
    return sender.hasPermission(PERMISSION_GIVE) || sender.hasPermission(PERMISSION_ADMIN);
  }

  private static CommandSender sender(CommandSourceStack source) {
    return source.getSender();
  }

  private static void sendMessage(CommandSender sender, String message) {
    CommandFeedback.send(sender, message);
  }
}
