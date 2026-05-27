package com.zxcmc.exort.core.feedback;

import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.text.ExortText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class PlayerFeedback {
  private static final long ACTION_BAR_COOLDOWN_MS = 900L;

  private final Lang lang;
  private final FeedbackCooldown cooldown = new FeedbackCooldown(ACTION_BAR_COOLDOWN_MS);

  public PlayerFeedback(Lang lang) {
    this.lang = lang;
  }

  public void info(Player player, String key, Object... params) {
    send(player, Style.INFO, lang.tr(key, params));
  }

  public void success(Player player, String key, Object... params) {
    send(player, Style.SUCCESS, lang.tr(key, params));
  }

  public void warn(Player player, String key, Object... params) {
    send(player, Style.WARN, lang.tr(key, params));
  }

  public void error(Player player, String key, Object... params) {
    send(player, Style.ERROR, lang.tr(key, params));
  }

  public void actionBar(Player player, String key, Object... params) {
    info(player, key, params);
  }

  public void infoMessage(Player player, String message) {
    send(player, Style.INFO, message);
  }

  public void warnMessage(Player player, String message) {
    send(player, Style.WARN, message);
  }

  public void errorMessage(Player player, String message) {
    send(player, Style.ERROR, message);
  }

  private void send(Player player, Style style, String message) {
    if (player == null || !player.isOnline() || message == null || message.isBlank()) {
      return;
    }
    if (!cooldown.shouldSend(
        player.getUniqueId(), style.name(), message, System.currentTimeMillis())) {
      return;
    }
    player.sendActionBar(Component.text(message, style.color()));
  }

  private enum Style {
    INFO(ExortText.INFO),
    SUCCESS(ExortText.SUCCESS),
    WARN(ExortText.WARNING),
    ERROR(ExortText.ERROR);

    private final NamedTextColor color;

    Style(NamedTextColor color) {
      this.color = color;
    }

    NamedTextColor color() {
      return color;
    }
  }
}
