package com.zxcmc.exort.feedback;

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

  @Test
  void reasonCooldownSuppressesChangingMessageParameters() {
    FeedbackCooldown cooldown = new FeedbackCooldown(900L);
    UUID playerId = UUID.randomUUID();

    assertTrue(cooldown.shouldSend(playerId, "NETWORK_TRAVERSAL_LIMIT", 1_000L, 900L));
    assertFalse(cooldown.shouldSend(playerId, "NETWORK_TRAVERSAL_LIMIT", 1_500L, 900L));
    assertTrue(cooldown.shouldSend(playerId, "CHUNK_LOADER_QUOTA", 1_500L, 1_500L));
  }

  @Test
  void staleEntriesDoNotKeepCooldownMapUnbounded() {
    FeedbackCooldown cooldown = new FeedbackCooldown(1_000_000L);
    UUID first = new UUID(0L, 1L);
    for (int index = 0; index < 8_300; index++) {
      assertTrue(cooldown.shouldSend(new UUID(0L, index + 1L), "reason", index, 1_000_000L));
    }

    assertTrue(cooldown.shouldSend(new UUID(1L, 1L), "reason", 700_000L, 1_000_000L));
    assertTrue(cooldown.shouldSend(first, "reason", 700_001L, 1_000_000L));
  }

  @Test
  void freshUniqueReasonsStillRespectTheHardEntryCap() {
    FeedbackCooldown cooldown = new FeedbackCooldown(1_000_000L);
    for (int index = 0; index < 8_300; index++) {
      assertTrue(
          cooldown.shouldSend(new UUID(0L, index + 1L), "fresh-" + index, 700_000L, 1_000_000L));
    }

    assertTrue(cooldown.size() <= 8_192);
  }
}
