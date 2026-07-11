package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeReloadCoordinatorTest {
  @Test
  void candidateConstructionFailureRestoresLastKnownGoodAfterClosingOldGeneration() {
    List<String> events = new ArrayList<>();
    RuntimeHandle<String> old = owned("old", events);
    RuntimeHandle<String> restored = owned("restored", events);

    RuntimeReloadCoordinator.Outcome<String> outcome =
        RuntimeReloadCoordinator.replace(
            old,
            () -> {
              events.add("candidate-factory");
              throw new IllegalStateException("candidate failed");
            },
            value -> events.add("publish-" + value),
            () -> restored,
            value -> events.add("publish-" + value));

    assertSame(restored, outcome.handle());
    assertTrue(outcome.restored());
    assertNotNull(outcome.failure());
    assertEquals(List.of("close-old", "candidate-factory", "publish-restored"), events);
  }

  @Test
  void failedCandidatePublicationClosesEveryCandidateResourceBeforeRollback() {
    List<String> events = new ArrayList<>();
    RuntimeHandle<String> candidate =
        RuntimeHandle.<String>builder("candidate")
            .own("listener", () -> events.add("close-listener"))
            .own("task", () -> events.add("close-task"))
            .own("ticket", () -> events.add("close-ticket"))
            .build();

    RuntimeReloadCoordinator.Outcome<String> outcome =
        RuntimeReloadCoordinator.replace(
            owned("old", events),
            () -> candidate,
            ignored -> {
              events.add("publish-candidate");
              throw new IllegalStateException("publish failed");
            },
            () -> RuntimeHandle.builder("restored").build(),
            value -> events.add("publish-" + value));

    assertTrue(outcome.restored());
    assertEquals(
        List.of(
            "close-old",
            "publish-candidate",
            "close-ticket",
            "close-task",
            "close-listener",
            "publish-restored"),
        events);
  }

  @Test
  void uncertainCandidateCleanupIsFatalAndDoesNotStartAnotherGeneration() {
    List<String> events = new ArrayList<>();
    RuntimeHandle<String> candidate =
        RuntimeHandle.<String>builder("candidate")
            .own(
                "task",
                () -> {
                  events.add("close-task");
                  throw new IllegalStateException("task survived");
                })
            .build();

    RuntimeReloadCoordinator.Outcome<String> outcome =
        RuntimeReloadCoordinator.replace(
            RuntimeHandle.builder("old").build(),
            () -> candidate,
            ignored -> {
              throw new IllegalStateException("publish failed");
            },
            () -> {
              events.add("rollback-factory");
              return RuntimeHandle.builder("restored").build();
            },
            ignored -> {});

    assertTrue(outcome.fatal());
    assertNull(outcome.handle());
    assertFalse(outcome.restored());
    assertEquals(List.of("close-task"), events);
    assertEquals(1, outcome.failure().getSuppressed().length);
  }

  @Test
  void factoryReportedPartialCleanupFailureDoesNotAttemptRollback() {
    List<String> events = new ArrayList<>();
    RuntimeConstructionException constructionFailure =
        new RuntimeConstructionException(
            new IllegalStateException("construction failed"),
            new IllegalStateException("listener survived"));

    RuntimeReloadCoordinator.Outcome<String> outcome =
        RuntimeReloadCoordinator.replace(
            RuntimeHandle.builder("old").build(),
            () -> {
              throw constructionFailure;
            },
            ignored -> {},
            () -> {
              events.add("rollback-factory");
              return RuntimeHandle.builder("restored").build();
            },
            ignored -> {});

    assertTrue(outcome.fatal());
    assertSame(constructionFailure, outcome.failure());
    assertTrue(events.isEmpty());
  }

  @Test
  void rollbackFailureIsReportedAsSuppressedAndLeavesNoPublishedRuntime() {
    IllegalStateException candidateFailure = new IllegalStateException("candidate failed");

    RuntimeReloadCoordinator.Outcome<String> outcome =
        RuntimeReloadCoordinator.replace(
            RuntimeHandle.builder("old").build(),
            () -> {
              throw candidateFailure;
            },
            ignored -> {},
            () -> {
              throw new IllegalArgumentException("rollback failed");
            },
            ignored -> {});

    assertTrue(outcome.fatal());
    assertSame(candidateFailure, outcome.failure());
    assertEquals(1, candidateFailure.getSuppressed().length);
  }

  @Test
  void successfulCandidateIsPublishedOnceAndReturnedWithoutRollback() {
    List<String> events = new ArrayList<>();
    RuntimeHandle<String> candidate = RuntimeHandle.builder("candidate").build();

    RuntimeReloadCoordinator.Outcome<String> outcome =
        RuntimeReloadCoordinator.replace(
            owned("old", events),
            () -> candidate,
            value -> events.add("publish-" + value),
            () -> {
              throw new AssertionError("rollback must not run");
            },
            ignored -> {});

    assertTrue(outcome.activated());
    assertSame(candidate, outcome.handle());
    assertNull(outcome.failure());
    assertEquals(List.of("close-old", "publish-candidate"), events);
  }

  private static RuntimeHandle<String> owned(String name, List<String> events) {
    return RuntimeHandle.<String>builder(name).own(name, () -> events.add("close-" + name)).build();
  }
}
