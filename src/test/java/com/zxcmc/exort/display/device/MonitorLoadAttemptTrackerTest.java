package com.zxcmc.exort.display.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MonitorLoadAttemptTrackerTest {
  @Test
  void cooldownSuppressesOnlyRecentAttemptForSameStorage() {
    MonitorLoadAttemptTracker tracker = new MonitorLoadAttemptTracker(4, 100L);

    assertTrue(tracker.tryStart("storage-a", 1_000L));
    assertFalse(tracker.tryStart("storage-a", 1_099L));
    assertTrue(tracker.tryStart("storage-b", 1_099L));
    assertTrue(tracker.tryStart("storage-a", 1_100L));
  }

  @Test
  void successfulLoadOrRemovedStorageClearsCooldownImmediately() {
    MonitorLoadAttemptTracker tracker = new MonitorLoadAttemptTracker(4, 100L);

    assertTrue(tracker.tryStart("storage-a", 1_000L));
    tracker.complete("storage-a");
    assertTrue(tracker.tryStart("storage-a", 1_001L));

    tracker.forget("storage-a");
    assertTrue(tracker.tryStart("storage-a", 1_002L));
  }

  @Test
  void oldestAttemptsAreEvictedAtHardLimit() {
    MonitorLoadAttemptTracker tracker = new MonitorLoadAttemptTracker(2, 1_000L);

    assertTrue(tracker.tryStart("storage-a", 1L));
    assertTrue(tracker.tryStart("storage-b", 2L));
    assertTrue(tracker.tryStart("storage-c", 3L));

    assertEquals(2, tracker.size());
    assertFalse(tracker.contains("storage-a"));
    assertTrue(tracker.contains("storage-b"));
    assertTrue(tracker.contains("storage-c"));
    assertTrue(tracker.tryStart("storage-a", 4L));
  }

  @Test
  void clearAndInvalidIdsLeaveNoRetainedState() {
    MonitorLoadAttemptTracker tracker = new MonitorLoadAttemptTracker(2, 100L);

    assertFalse(tracker.tryStart(null, 1L));
    assertFalse(tracker.tryStart(" ", 1L));
    assertTrue(tracker.tryStart("storage-a", 1L));
    tracker.clear();

    assertEquals(0, tracker.size());
  }

  @Test
  void constructorRejectsInvalidBounds() {
    assertThrows(IllegalArgumentException.class, () -> new MonitorLoadAttemptTracker(0, 1L));
    assertThrows(IllegalArgumentException.class, () -> new MonitorLoadAttemptTracker(1, -1L));
  }
}
