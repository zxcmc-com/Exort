package com.zxcmc.exort.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.i18n.Lang;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PlayerFeedbackTest {
  @Test
  void frequentDenialUsesThrottledActionBar() {
    FeedbackTarget target = target();
    PlayerFeedback feedback = new PlayerFeedback(new Lang(null));

    feedback.respond(
        target.player(), FeedbackReason.CHUNK_LOADER_QUOTA, "message.chunk_loader_limit_reached");
    feedback.respond(
        target.player(), FeedbackReason.CHUNK_LOADER_QUOTA, "message.chunk_loader_limit_reached");

    assertEquals(1, target.actionBars().get());
    assertEquals(0, target.chatMessages().get());
  }

  @Test
  void rareStorageFailureUsesChat() {
    FeedbackTarget target = target();
    PlayerFeedback feedback = new PlayerFeedback(new Lang(null));

    feedback.respond(
        target.player(), FeedbackReason.STORAGE_FAILURE, "message.storage_load_failed");

    assertEquals(0, target.actionBars().get());
    assertEquals(1, target.chatMessages().get());
  }

  @Test
  void separateReasonsHaveIndependentActionBarCooldowns() {
    FeedbackTarget target = target();
    PlayerFeedback feedback = new PlayerFeedback(new Lang(null));

    feedback.respond(target.player(), FeedbackReason.STORAGE_LOADING, "message.storage_loading");
    feedback.respond(
        target.player(), FeedbackReason.NETWORK_TRAVERSAL_LIMIT, "message.wire.hard_cap", 65, 64);

    assertEquals(2, target.actionBars().get());
    assertEquals(0, target.chatMessages().get());
  }

  @Test
  void separateWirelessDenialsDoNotHideEachOther() {
    FeedbackTarget target = target();
    PlayerFeedback feedback = new PlayerFeedback(new Lang(null));

    feedback.respond(
        target.player(), FeedbackReason.WIRELESS_ACCESS, "message.wireless.not_linked");
    feedback.respond(
        target.player(), FeedbackReason.WIRELESS_ACCESS, "message.wireless.out_of_range");

    assertEquals(2, target.actionBars().get());
    assertEquals(0, target.chatMessages().get());
  }

  private static FeedbackTarget target() {
    UUID playerId = UUID.randomUUID();
    AtomicInteger actionBars = new AtomicInteger();
    AtomicInteger chatMessages = new AtomicInteger();
    Player player =
        (Player)
            Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                  return switch (method.getName()) {
                    case "isOnline" -> true;
                    case "getUniqueId" -> playerId;
                    case "locale" -> Locale.US;
                    case "sendActionBar" -> {
                      actionBars.incrementAndGet();
                      yield null;
                    }
                    case "sendMessage" -> {
                      chatMessages.incrementAndGet();
                      yield null;
                    }
                    case "toString" -> "feedback-player";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                  };
                });
    return new FeedbackTarget(player, actionBars, chatMessages);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0.0F;
    if (type == double.class) return 0.0D;
    if (type == char.class) return '\0';
    return null;
  }

  private record FeedbackTarget(
      Player player, AtomicInteger actionBars, AtomicInteger chatMessages) {}
}
