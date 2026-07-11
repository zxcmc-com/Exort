package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeHandleTest {
  @Test
  void closesOwnedResourcesInReverseRegistrationOrderOnlyOnce() {
    List<String> closed = new ArrayList<>();
    RuntimeHandle<String> handle =
        RuntimeHandle.<String>builder("runtime")
            .own("first", () -> closed.add("first"))
            .own("second", () -> closed.add("second"))
            .build();

    handle.close();
    handle.close();

    assertEquals(List.of("second", "first"), closed);
  }

  @Test
  void closeAttemptsEveryResourceAndPreservesAllFailures() {
    List<String> closed = new ArrayList<>();
    RuntimeHandle<String> handle =
        RuntimeHandle.<String>builder("runtime")
            .own(
                "first",
                () -> {
                  closed.add("first");
                  throw new IllegalStateException("first failed");
                })
            .own(
                "second",
                () -> {
                  closed.add("second");
                  throw new IllegalArgumentException("second failed");
                })
            .build();

    RuntimeException failure = assertThrows(RuntimeException.class, handle::close);

    assertEquals(List.of("second", "first"), closed);
    assertEquals(2, failure.getSuppressed().length);
  }

  @Test
  void incompleteConstructionScopeRollsBackButCompletedScopeTransfersOwnership() {
    List<String> events = new ArrayList<>();
    try (RuntimeHandle.Scope scope = RuntimeHandle.scope()) {
      scope.own("candidate", () -> events.add("candidate-rollback"));
    }
    RuntimeHandle<String> completed;
    try (RuntimeHandle.Scope scope = RuntimeHandle.scope()) {
      scope.own("active", () -> events.add("active-close"));
      completed = scope.complete("ready");
    }

    assertEquals(List.of("candidate-rollback"), events);
    completed.close();
    assertEquals(List.of("candidate-rollback", "active-close"), events);
  }
}
