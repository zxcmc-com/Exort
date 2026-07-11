package com.zxcmc.exort.integration;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Idempotently owns one optional-provider adapter and publishes only live instances.
 *
 * <p>This lifecycle is confined to the Bukkit server thread.
 */
public final class OptionalProviderLifecycle<T> implements AutoCloseable {
  private final Consumer<T> publisher;
  private final Consumer<T> disposer;
  private T current;

  public OptionalProviderLifecycle(Consumer<T> publisher, Consumer<T> disposer) {
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    this.disposer = Objects.requireNonNull(disposer, "disposer");
  }

  public boolean enable(Supplier<? extends T> factory) {
    Objects.requireNonNull(factory, "factory");
    if (current != null) {
      return true;
    }
    T candidate = factory.get();
    if (candidate == null) {
      return false;
    }
    try {
      publisher.accept(candidate);
      current = candidate;
      return true;
    } catch (RuntimeException | LinkageError publishFailure) {
      try {
        disposer.accept(candidate);
      } catch (RuntimeException | LinkageError cleanupFailure) {
        publishFailure.addSuppressed(cleanupFailure);
      }
      throw publishFailure;
    }
  }

  public void disable() {
    T previous = current;
    if (previous == null) {
      return;
    }
    current = null;
    RuntimeException failure = null;
    try {
      publisher.accept(null);
    } catch (RuntimeException | LinkageError publishFailure) {
      failure =
          new IllegalStateException("Failed to clear optional-provider adapter", publishFailure);
    }
    try {
      disposer.accept(previous);
    } catch (RuntimeException | LinkageError cleanupFailure) {
      if (failure == null) {
        failure = new IllegalStateException("Failed to close optional-provider adapter");
      }
      failure.addSuppressed(cleanupFailure);
    }
    if (failure != null) {
      throw failure;
    }
  }

  /** Rebuilds the adapter after the enabled-provider topology changes. */
  public boolean refresh(Supplier<? extends T> factory) {
    Objects.requireNonNull(factory, "factory");
    T previous = current;
    T candidate = factory.get();
    if (candidate == null) {
      return false;
    }
    try {
      publisher.accept(candidate);
    } catch (RuntimeException | LinkageError publishFailure) {
      try {
        disposer.accept(candidate);
      } catch (RuntimeException | LinkageError cleanupFailure) {
        publishFailure.addSuppressed(cleanupFailure);
      }
      if (previous != null) {
        try {
          publisher.accept(previous);
        } catch (RuntimeException | LinkageError restoreFailure) {
          publishFailure.addSuppressed(restoreFailure);
        }
      }
      throw publishFailure;
    }
    current = candidate;
    if (previous != null) {
      disposer.accept(previous);
    }
    return true;
  }

  public T current() {
    return current;
  }

  @Override
  public void close() {
    disable();
  }
}
