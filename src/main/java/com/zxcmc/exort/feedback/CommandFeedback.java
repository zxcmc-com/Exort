package com.zxcmc.exort.feedback;

import com.zxcmc.exort.text.ExortText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public final class CommandFeedback {
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
    sender.sendMessage(prefixed(message));
  }

  public static void sendBlock(CommandSender sender, String title, List<String> lines) {
    if (title == null || title.isBlank()) {
      return;
    }
    List<Component> components = new ArrayList<>();
    if (lines != null) {
      for (String line : lines) {
        if (line != null && !line.isBlank()) {
          components.add(ExortText.colored(line, ExortText.INFO));
        }
      }
    }
    sendBlock(sender, ExortText.colored(title, ExortText.INFO), components);
  }

  public static void sendBlock(CommandSender sender, Component title, List<Component> lines) {
    if (sender == null || title == null) {
      return;
    }
    sender.sendMessage(block(title, lines));
  }

  public static Component block(Component title, List<Component> lines) {
    return prefixed(lines(title, lines));
  }

  private static Component lines(Component title, List<Component> lines) {
    Component message = Objects.requireNonNull(title, "title");
    if (lines == null || lines.isEmpty()) {
      return message;
    }
    for (Component line : lines) {
      if (line != null) {
        message = message.append(Component.newline()).append(line);
      }
    }
    return message;
  }

  public static Component commandLine(String syntax, String description, String hoverText) {
    return command(syntax, hoverText)
        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
        .append(Component.text(description == null ? "" : description, ExortText.INFO));
  }

  public static Component command(String syntax, String hoverText) {
    String safeSyntax = Objects.requireNonNull(syntax, "syntax");
    String suggestion = suggestedCommand(safeSyntax);
    ClickEvent click = ClickEvent.suggestCommand(suggestion);
    HoverEvent<Component> hover =
        HoverEvent.showText(
            ExortText.colored(
                hoverText == null ? suggestion : hoverText.replace(safeSyntax, suggestion),
                ExortText.INFO));
    String displaySyntax = safeSyntax.replaceAll("\\s*\\|\\s*", "|");
    Component result = Component.empty();
    int segmentStart = 0;
    for (int i = 0; i < displaySyntax.length(); i++) {
      char ch = displaySyntax.charAt(i);
      if (ch != '<' && ch != '[') {
        continue;
      }
      result =
          appendSyntaxPart(
              result, displaySyntax.substring(segmentStart, i), NamedTextColor.GREEN, click, hover);
      char closing = ch == '<' ? '>' : ']';
      int end = displaySyntax.indexOf(closing, i + 1);
      if (end < 0) {
        return appendSyntaxPart(
            result,
            displaySyntax.substring(i),
            ch == '<' ? NamedTextColor.GOLD : NamedTextColor.YELLOW,
            click,
            hover);
      }
      result =
          appendSyntaxPart(
              result,
              displaySyntax.substring(i, end + 1),
              ch == '<' ? NamedTextColor.GOLD : NamedTextColor.YELLOW,
              click,
              hover);
      i = end;
      segmentStart = end + 1;
    }
    return appendSyntaxPart(
        result, displaySyntax.substring(segmentStart), NamedTextColor.GREEN, click, hover);
  }

  public static Component prefixed(Component message) {
    return PREFIX.append(message);
  }

  private static Component commandPart(
      String text, NamedTextColor color, ClickEvent click, HoverEvent<Component> hover) {
    return Component.text(text, color).clickEvent(click).hoverEvent(hover);
  }

  private static Component appendSyntaxPart(
      Component result,
      String text,
      NamedTextColor color,
      ClickEvent click,
      HoverEvent<Component> hover) {
    if (text.isEmpty()) {
      return result;
    }
    int partStart = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) != '|') {
        continue;
      }
      result = appendText(result, text.substring(partStart, i), color, click, hover);
      result = result.append(commandPart(" | ", NamedTextColor.DARK_GRAY, click, hover));
      partStart = i + 1;
    }
    return appendText(result, text.substring(partStart), color, click, hover);
  }

  private static Component appendText(
      Component result,
      String text,
      NamedTextColor color,
      ClickEvent click,
      HoverEvent<Component> hover) {
    return text.isEmpty() ? result : result.append(commandPart(text, color, click, hover));
  }

  private static String suggestedCommand(String syntax) {
    String[] parts = syntax.trim().split("\\s+");
    List<String> fixed = new ArrayList<>();
    for (String part : parts) {
      if (part.startsWith("<") || part.startsWith("[") || part.contains("|")) {
        break;
      }
      fixed.add(part);
    }
    if (fixed.isEmpty()) {
      return "";
    }
    String suggestion = String.join(" ", fixed);
    return fixed.size() == parts.length ? suggestion : suggestion + " ";
  }
}
