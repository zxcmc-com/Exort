package com.zxcmc.exort.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class CommandFeedbackTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void blockOutputPrefixesOnlyTheFirstLine() {
    Component block =
        CommandFeedback.block(
            Component.text("Commands:"),
            List.of(Component.text("/exort inventory"), Component.text("/exort give")));

    String plain = PLAIN.serialize(block);

    assertEquals("[Exort] Commands:\n/exort inventory\n/exort give", plain);
    assertEquals(1, count(plain, "[Exort]"));
  }

  @Test
  void commandFragmentKeepsPlainSyntaxAndSuggestsCommand() {
    Component command = CommandFeedback.command("/exort give <player>", "Click");

    assertEquals("/exort give <player>", PLAIN.serialize(command));
    ClickEvent click = firstClickEvent(command);
    assertNotNull(click);
    assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.action());
    ClickEvent.Payload payload = click.payload();
    assertTrue(payload instanceof ClickEvent.Payload.Text);
    assertEquals("/exort give ", ((ClickEvent.Payload.Text) payload).value());
  }

  @Test
  void commandSuggestionDropsChoicesAndOptionalArguments() {
    Component command =
        CommandFeedback.command(
            "/exort debug verbose <cache|worldedit|pick> start|stop [mode]", "Click");

    assertEquals(
        "/exort debug verbose <cache | worldedit | pick> start | stop [mode]",
        PLAIN.serialize(command));
    ClickEvent click = firstClickEvent(command);
    assertNotNull(click);
    assertEquals(ClickEvent.Action.SUGGEST_COMMAND, click.action());
    ClickEvent.Payload payload = click.payload();
    assertTrue(payload instanceof ClickEvent.Payload.Text);
    assertEquals("/exort debug verbose ", ((ClickEvent.Payload.Text) payload).value());
  }

  @Test
  void commandSuggestionKeepsCompleteCommandsWithoutTrailingSpace() {
    Component command = CommandFeedback.command("/exort inventory", "Click");

    ClickEvent click = firstClickEvent(command);

    assertNotNull(click);
    assertEquals("/exort inventory", ((ClickEvent.Payload.Text) click.payload()).value());
  }

  @Test
  void commandSuggestionKeepsConcreteGiveStoragePrefix() {
    Component command =
        CommandFeedback.command("/exort give phantomfighterxx storage <tier> [amount]", "Click");

    assertEquals("/exort give phantomfighterxx storage <tier> [amount]", PLAIN.serialize(command));
    ClickEvent click = firstClickEvent(command);
    assertNotNull(click);
    assertEquals(
        "/exort give phantomfighterxx storage ",
        ((ClickEvent.Payload.Text) click.payload()).value());
  }

  @Test
  void commandFragmentsAreGreenAndNotUnderlined() {
    Component command = CommandFeedback.command("/exort give <player>", "Click");

    Component first = firstClickableComponent(command);

    assertNotNull(first);
    assertEquals(NamedTextColor.GREEN, first.color());
    assertEquals(TextDecoration.State.NOT_SET, first.decoration(TextDecoration.UNDERLINED));
  }

  private static int count(String value, String needle) {
    int count = 0;
    int index = 0;
    while ((index = value.indexOf(needle, index)) >= 0) {
      count++;
      index += needle.length();
    }
    return count;
  }

  private static ClickEvent firstClickEvent(Component component) {
    Component clickable = firstClickableComponent(component);
    return clickable == null ? null : clickable.clickEvent();
  }

  private static Component firstClickableComponent(Component component) {
    if (component.clickEvent() != null) {
      return component;
    }
    for (Component child : component.children()) {
      Component clickable = firstClickableComponent(child);
      if (clickable != null) {
        return clickable;
      }
    }
    return null;
  }
}
