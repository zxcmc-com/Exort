package com.zxcmc.exort.core.text;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ExortText {
  public static final NamedTextColor PREFIX = NamedTextColor.AQUA;
  public static final NamedTextColor INFO = NamedTextColor.GRAY;
  public static final NamedTextColor SUCCESS = NamedTextColor.GREEN;
  public static final NamedTextColor WARNING = NamedTextColor.YELLOW;
  public static final NamedTextColor ERROR = NamedTextColor.DARK_RED;

  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
  private static final LegacyComponentSerializer LEGACY_SECTION =
      LegacyComponentSerializer.legacySection();

  private ExortText() {}

  public static Component plain(String value) {
    return Component.text(value == null ? "" : value);
  }

  public static Component colored(String value, TextColor color) {
    Component component = plain(value);
    return color == null ? component : component.color(color);
  }

  public static Component itemText(String value) {
    return plain(value).decoration(TextDecoration.ITALIC, false);
  }

  public static NamedTextColor monitorFillColor(double freeRatio) {
    if (freeRatio <= 0.05) {
      return NamedTextColor.RED;
    }
    if (freeRatio <= 0.30) {
      return NamedTextColor.GOLD;
    }
    return NamedTextColor.WHITE;
  }

  public static Component monitorFillText(String value, double freeRatio) {
    return colored(value, monitorFillColor(freeRatio));
  }

  public static Component configRichText(String value) {
    if (value == null || value.isEmpty()) {
      return Component.empty();
    }
    if (value.indexOf('§') >= 0) {
      return LEGACY_SECTION.deserialize(value);
    }
    try {
      return MINI_MESSAGE.deserialize(value);
    } catch (RuntimeException ignored) {
      return plain(value);
    }
  }

  public static Component withFont(Component component, String font) {
    if (component == null || font == null || font.isBlank()) {
      return component == null ? Component.empty() : component;
    }
    try {
      return component.font(Key.key(font));
    } catch (IllegalArgumentException ignored) {
      return component;
    }
  }

  public static Component withPrefix(Component prefix, Component content) {
    Component safeContent = content == null ? Component.empty() : content;
    if (prefix == null) {
      return safeContent;
    }
    return Component.empty().append(prefix).append(safeContent);
  }
}
