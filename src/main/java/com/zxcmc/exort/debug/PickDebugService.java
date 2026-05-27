package com.zxcmc.exort.debug;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.text.ExortText;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PickDebugService {
  public enum Mode {
    COMPACT,
    NORMAL,
    FULL;

    public static Mode fromString(String raw) {
      if (raw == null || raw.isBlank()) return NORMAL;
      try {
        return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
  }

  private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
  private volatile boolean consoleExplicit;
  private volatile Mode mode = Mode.NORMAL;

  public PickDebugService(ExortPlugin plugin) {}

  public boolean isEnabled() {
    return consoleExplicit || !viewers.isEmpty();
  }

  public Mode getMode() {
    return mode;
  }

  public void start(CommandSender sender, Mode mode) {
    if (mode != null) {
      this.mode = mode;
    }
    if (sender instanceof Player player) {
      viewers.add(player.getUniqueId());
    } else {
      consoleExplicit = true;
    }
  }

  public void stop(CommandSender sender) {
    if (sender instanceof Player player) {
      viewers.remove(player.getUniqueId());
    } else {
      consoleExplicit = false;
    }
  }

  public void record(String message) {
    if (!isEnabled() || message == null) return;
    if (mode == Mode.COMPACT && !message.contains("handled") && !message.contains("miss")) {
      return;
    }
    send(formatMessage(message, NamedTextColor.GRAY));
  }

  public void recordFull(String message) {
    if (!isEnabled() || mode != Mode.FULL || message == null) return;
    send(formatMessage(message, NamedTextColor.DARK_GRAY));
  }

  private void send(Component line) {
    if (line == null) return;
    if (consoleExplicit || !viewers.isEmpty()) {
      Bukkit.getConsoleSender().sendMessage(line);
    }
    for (UUID viewerId : viewers) {
      Player player = Bukkit.getPlayer(viewerId);
      if (player == null || !player.isOnline()) {
        viewers.remove(viewerId);
        continue;
      }
      player.sendMessage(line);
    }
  }

  private Component formatMessage(String message, NamedTextColor color) {
    return Component.text("[Exort] ", ExortText.PREFIX)
        .append(Component.text("pick ", NamedTextColor.GOLD))
        .append(Component.text(message, color));
  }
}
