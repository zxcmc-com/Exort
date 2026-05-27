package com.zxcmc.exort.core.feedback;

import com.zxcmc.exort.core.logging.ExortLog;
import com.zxcmc.exort.core.text.ExortText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

public final class CommandFeedback {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();
  private static final Component PREFIX = Component.text("[Exort] ", ExortText.PREFIX);

  private CommandFeedback() {}

  public static void send(CommandSender sender, String message) {
    if (sender == null || message == null || message.isBlank()) {
      return;
    }
    send(sender, ExortText.colored(message, ExortText.INFO));
  }

  public static void send(CommandSender sender, Component message) {
    if (sender == null || message == null) {
      return;
    }
    if (sender instanceof ConsoleCommandSender) {
      ExortLog.info(PLAIN.serialize(message));
      return;
    }
    sender.sendMessage(PREFIX.append(message));
  }
}
