package com.zxcmc.exort.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OptionalProviderLifecycleTest {
  @Test
  void repeatedEnableAndDisableAreIdempotent() {
    List<String> events = new ArrayList<>();
    AtomicInteger creations = new AtomicInteger();
    try (OptionalProviderLifecycle<String> lifecycle =
        new OptionalProviderLifecycle<>(
            value -> events.add("publish-" + value), value -> events.add("close-" + value))) {
      assertTrue(lifecycle.enable(() -> "provider-" + creations.incrementAndGet()));
      assertTrue(lifecycle.enable(() -> "provider-" + creations.incrementAndGet()));
      lifecycle.disable();
      lifecycle.disable();

      assertEquals(1, creations.get());
      assertEquals(List.of("publish-provider-1", "publish-null", "close-provider-1"), events);
      assertNull(lifecycle.current());
    }
  }

  @Test
  void disableThenEnablePublishesACompletelyNewGeneration() {
    List<String> published = new ArrayList<>();
    List<String> closed = new ArrayList<>();
    try (OptionalProviderLifecycle<String> lifecycle =
        new OptionalProviderLifecycle<>(published::add, closed::add)) {
      lifecycle.enable(() -> "first");
      lifecycle.disable();
      lifecycle.enable(() -> "second");

      assertEquals(Arrays.asList("first", null, "second"), published);
      assertEquals(List.of("first"), closed);
      assertSame("second", lifecycle.current());
    }
  }

  @Test
  void failedPublicationDisposesCandidateAndLeavesLifecycleDisabled() {
    List<String> closed = new ArrayList<>();
    try (OptionalProviderLifecycle<String> lifecycle =
        new OptionalProviderLifecycle<>(
            ignored -> {
              throw new IllegalStateException("publish failed");
            },
            closed::add)) {
      assertThrows(IllegalStateException.class, () -> lifecycle.enable(() -> "candidate"));

      assertEquals(List.of("candidate"), closed);
      assertNull(lifecycle.current());
    }
  }

  @Test
  void refreshReplacesAnActiveAdapterWhenProviderTopologyChanges() {
    List<String> published = new ArrayList<>();
    List<String> closed = new ArrayList<>();
    try (OptionalProviderLifecycle<String> lifecycle =
        new OptionalProviderLifecycle<>(published::add, closed::add)) {
      lifecycle.enable(() -> "worldedit");

      assertTrue(lifecycle.refresh(() -> "fawe"));

      assertEquals(Arrays.asList("worldedit", "fawe"), published);
      assertEquals(List.of("worldedit"), closed);
      assertSame("fawe", lifecycle.current());
    }
  }

  @Test
  void failedRefreshKeepsTheLastWorkingAdapterPublished() {
    List<String> published = new ArrayList<>();
    List<String> closed = new ArrayList<>();
    try (OptionalProviderLifecycle<String> lifecycle =
        new OptionalProviderLifecycle<>(published::add, closed::add)) {
      lifecycle.enable(() -> "worldedit");

      assertEquals(false, lifecycle.refresh(() -> null));

      assertEquals(List.of("worldedit"), published);
      assertTrue(closed.isEmpty());
      assertSame("worldedit", lifecycle.current());
    }
  }
}
