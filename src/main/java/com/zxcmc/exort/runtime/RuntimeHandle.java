package com.zxcmc.exort.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one runtime generation and releases its resources in strict reverse creation order. */
public final class RuntimeHandle<T> implements AutoCloseable {
  private final T value;
  private final Deque<OwnedResource> resources;
  private final AtomicBoolean closed = new AtomicBoolean();

  private RuntimeHandle(T value, Deque<OwnedResource> resources) {
    this.value = Objects.requireNonNull(value, "value");
    this.resources = new ArrayDeque<>(resources);
  }

  public static <T> Builder<T> builder(T value) {
    return new Builder<>(value);
  }

  public static Scope scope() {
    return new Scope();
  }

  public T value() {
    return value;
  }

  public boolean isClosed() {
    return closed.get();
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    RuntimeException aggregate = null;
    while (!resources.isEmpty()) {
      OwnedResource resource = resources.removeLast();
      try {
        resource.cleanup().run();
      } catch (RuntimeException | LinkageError error) {
        if (aggregate == null) {
          aggregate = new IllegalStateException("Failed to close runtime resources");
        }
        aggregate.addSuppressed(
            new IllegalStateException("Cleanup failed for " + resource.name(), error));
      }
    }
    if (aggregate != null) {
      throw aggregate;
    }
  }

  public static final class Builder<T> {
    private final T value;
    private final Deque<OwnedResource> resources = new ArrayDeque<>();

    private Builder(T value) {
      this.value = Objects.requireNonNull(value, "value");
    }

    public Builder<T> own(String name, Runnable cleanup) {
      resources.addLast(
          new OwnedResource(
              Objects.requireNonNull(name, "name"), Objects.requireNonNull(cleanup, "cleanup")));
      return this;
    }

    public RuntimeHandle<T> build() {
      return new RuntimeHandle<>(value, resources);
    }
  }

  /** Mutable construction scope. Closing an incomplete scope rolls back every owned resource. */
  public static final class Scope implements AutoCloseable {
    private final Deque<OwnedResource> resources = new ArrayDeque<>();
    private boolean completed;

    public Scope own(String name, Runnable cleanup) {
      if (completed) {
        throw new IllegalStateException("Runtime construction scope is already completed");
      }
      resources.addLast(
          new OwnedResource(
              Objects.requireNonNull(name, "name"), Objects.requireNonNull(cleanup, "cleanup")));
      return this;
    }

    public <T> RuntimeHandle<T> complete(T value) {
      if (completed) {
        throw new IllegalStateException("Runtime construction scope is already completed");
      }
      completed = true;
      RuntimeHandle<T> handle = new RuntimeHandle<>(value, resources);
      resources.clear();
      return handle;
    }

    @Override
    public void close() {
      if (completed || resources.isEmpty()) {
        return;
      }
      completed = true;
      RuntimeHandle<Object> rollback = new RuntimeHandle<>(this, resources);
      resources.clear();
      rollback.close();
    }
  }

  private record OwnedResource(String name, Runnable cleanup) {}
}
