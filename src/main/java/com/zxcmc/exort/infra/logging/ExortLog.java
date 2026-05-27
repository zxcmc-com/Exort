package com.zxcmc.exort.infra.logging;

import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExortLog {
  private static final String ANSI_RESET = "\u001B[0m";
  private static final String ANSI_AQUA = "\u001B[96m";
  private static final String ANSI_GRAY = "\u001B[37m";
  private static final String ANSI_GREEN = "\u001B[92m";
  private static final String ANSI_YELLOW = "\u001B[93m";
  private static final String ANSI_RED = "\u001B[91m";
  private static final String ANSI_PREFIX = ANSI_AQUA + "[Exort]" + ANSI_RESET + " ";

  private ExortLog() {}

  public static void info(String message) {
    send(message, ANSI_GRAY);
  }

  public static void success(String message) {
    send(message, ANSI_GREEN);
  }

  public static void warn(String message) {
    send(message, ANSI_YELLOW);
  }

  public static void warnCommand(String before, String command, String after) {
    send(before + ANSI_AQUA + command + ANSI_YELLOW + after, ANSI_YELLOW);
  }

  public static void error(String message) {
    send(message, ANSI_RED);
  }

  public static void log(JavaPlugin plugin, Level level, String message, Throwable err) {
    plugin.getLogger().log(level, message, unwrap(err));
  }

  public static Throwable unwrap(Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
  }

  public static NamedTextColor prefixColor() {
    return NamedTextColor.AQUA;
  }

  private static void send(String message, String ansiColor) {
    if (message == null || message.isBlank()) {
      return;
    }
    Bukkit.getConsoleSender().sendMessage(ANSI_PREFIX + ansiColor + message + ANSI_RESET);
  }
}
