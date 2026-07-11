package com.zxcmc.exort.runtime;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Performs the destructive activation phase of a runtime reload with last-known-good rollback. */
public final class RuntimeReloadCoordinator {
  private RuntimeReloadCoordinator() {}

  public static <T> Outcome<T> replace(
      RuntimeHandle<T> current,
      Supplier<RuntimeHandle<T>> candidateFactory,
      Consumer<T> candidatePublisher,
      Supplier<RuntimeHandle<T>> rollbackFactory,
      Consumer<T> rollbackPublisher) {
    Objects.requireNonNull(current, "current");
    Objects.requireNonNull(candidateFactory, "candidateFactory");
    Objects.requireNonNull(candidatePublisher, "candidatePublisher");
    Objects.requireNonNull(rollbackFactory, "rollbackFactory");
    Objects.requireNonNull(rollbackPublisher, "rollbackPublisher");

    try {
      current.close();
    } catch (RuntimeException | LinkageError closeFailure) {
      return Outcome.fatal(closeFailure);
    }

    Activation<T> candidate = activate(candidateFactory, candidatePublisher);
    if (candidate.handle() != null) {
      return Outcome.activated(candidate.handle());
    }
    if (!candidate.cleanupComplete()) {
      return Outcome.fatal(candidate.failure());
    }

    Activation<T> rollback = activate(rollbackFactory, rollbackPublisher);
    if (rollback.handle() != null) {
      return Outcome.restored(rollback.handle(), candidate.failure());
    }
    candidate.failure().addSuppressed(rollback.failure());
    return Outcome.fatal(candidate.failure());
  }

  private static <T> Activation<T> activate(
      Supplier<RuntimeHandle<T>> factory, Consumer<T> publisher) {
    RuntimeHandle<T> handle = null;
    try {
      handle = Objects.requireNonNull(factory.get(), "runtime factory returned null");
      publisher.accept(handle.value());
      return Activation.success(handle);
    } catch (RuntimeException | LinkageError activationFailure) {
      if (handle == null) {
        return Activation.failed(
            activationFailure, !(activationFailure instanceof RuntimeConstructionException));
      }
      try {
        handle.close();
        return Activation.failed(activationFailure, true);
      } catch (RuntimeException | LinkageError cleanupFailure) {
        activationFailure.addSuppressed(cleanupFailure);
        return Activation.failed(activationFailure, false);
      }
    }
  }

  public record Outcome<T>(RuntimeHandle<T> handle, Throwable failure, boolean restored) {
    private static <T> Outcome<T> activated(RuntimeHandle<T> handle) {
      return new Outcome<>(handle, null, false);
    }

    private static <T> Outcome<T> restored(RuntimeHandle<T> handle, Throwable failure) {
      return new Outcome<>(handle, failure, true);
    }

    private static <T> Outcome<T> fatal(Throwable failure) {
      return new Outcome<>(null, Objects.requireNonNull(failure, "failure"), false);
    }

    public boolean activated() {
      return handle != null && failure == null;
    }

    public boolean fatal() {
      return handle == null;
    }
  }

  private record Activation<T>(
      RuntimeHandle<T> handle, Throwable failure, boolean cleanupComplete) {
    private static <T> Activation<T> success(RuntimeHandle<T> handle) {
      return new Activation<>(handle, null, true);
    }

    private static <T> Activation<T> failed(Throwable failure, boolean cleanupComplete) {
      return new Activation<>(null, Objects.requireNonNull(failure, "failure"), cleanupComplete);
    }
  }
}
