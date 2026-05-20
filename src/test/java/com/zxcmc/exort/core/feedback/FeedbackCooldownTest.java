package com.zxcmc.exort.core.feedback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeedbackCooldownTest {
  @Test
  void suppressesSameMessageInsideCooldown() {
    FeedbackCooldown cooldown = new FeedbackCooldown(900L);
    UUID playerId = UUID.randomUUID();

    assertTrue(cooldown.shouldSend(playerId, "ERROR", "Linked storage not found.", 1_000L));
    assertFalse(cooldown.shouldSend(playerId, "ERROR", "Linked storage not found.", 1_500L));
  }

  @Test
  void allowsDifferentStyleOrMessageInsideCooldown() {
    FeedbackCooldown cooldown = new FeedbackCooldown(900L);
    UUID playerId = UUID.randomUUID();

    assertTrue(cooldown.shouldSend(playerId, "ERROR", "Linked storage not found.", 1_000L));
    assertTrue(cooldown.shouldSend(playerId, "WARN", "Linked storage not found.", 1_100L));
    assertTrue(cooldown.shouldSend(playerId, "WARN", "Wireless terminal is out of range.", 1_200L));
  }

  @Test
  void allowsSameMessageAfterCooldown() {
    FeedbackCooldown cooldown = new FeedbackCooldown(900L);
    UUID playerId = UUID.randomUUID();

    assertTrue(cooldown.shouldSend(playerId, "ERROR", "Linked storage not found.", 1_000L));
    assertTrue(cooldown.shouldSend(playerId, "ERROR", "Linked storage not found.", 1_900L));
  }
}
