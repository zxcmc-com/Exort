package com.zxcmc.exort.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one runtime generation and releases its resources in strict reverse creation order. */
public final class RuntimeHandle<T> implements AutoCloseable {
  private final T value;
  private final Deque<OwnedResource> resources;
  private final RuntimeGenerationScope generation;
  private final AtomicBoolean closed = new AtomicBoolean();

  private RuntimeHandle(
      T value, Deque<OwnedResource> resources, RuntimeGenerationScope generation) {
    this.value = Objects.requireNonNull(value, "value");
    this.resources = new ArrayDeque<>(resources);
    this.generation = generation;
  }

  public static <T> Builder<T> builder(T value) {
    return new Builder<>(value);
  }

  public static Scope scope() {
    return new Scope(null);
  }

  public static Scope scope(RuntimeGenerationScope generation) {
    return new Scope(Objects.requireNonNull(generation, "generation"));
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
    if (generation != null) {
      try {
        generation.close();
      } catch (RuntimeException | LinkageError error) {
        aggregate = cleanupFailure(aggregate, "runtime generation", error);
      }
    }
    while (!resources.isEmpty()) {
      OwnedResource resource = resources.removeLast();
      try {
        resource.cleanup().run();
      } catch (RuntimeException | LinkageError error) {
        aggregate = cleanupFailure(aggregate, resource.name(), error);
      } finally {
        if (resource.censusToken() != null) {
          resource.censusToken().close();
        }
      }
    }
    if (generation != null) {
      generation.finishCleanup();
    }
    if (aggregate != null) {
      throw aggregate;
    }
  }

  private static RuntimeException cleanupFailure(
      RuntimeException aggregate, String resourceName, Throwable error) {
    RuntimeException failure = aggregate;
    if (failure == null) {
      failure = new IllegalStateException("Failed to close runtime resources");
    }
    failure.addSuppressed(new IllegalStateException("Cleanup failed for " + resourceName, error));
    return failure;
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
              Objects.requireNonNull(name, "name"),
              Objects.requireNonNull(cleanup, "cleanup"),
              null));
      return this;
    }

    public RuntimeHandle<T> build() {
      return new RuntimeHandle<>(value, resources, null);
    }
  }

  /** Mutable construction scope. Closing an incomplete scope rolls back every owned resource. */
  public static final class Scope implements AutoCloseable {
    private final Deque<OwnedResource> resources = new ArrayDeque<>();
    private final RuntimeGenerationScope generation;
    private boolean completed;

    private Scope(RuntimeGenerationScope generation) {
      this.generation = generation;
    }

    public Scope own(String name, Runnable cleanup) {
      return own(RuntimeGenerationScope.ResourceKind.SERVICE, name, cleanup);
    }

    public Scope own(RuntimeGenerationScope.ResourceKind kind, String name, Runnable cleanup) {
      if (completed) {
        throw new IllegalStateException("Runtime construction scope is already completed");
      }
      Objects.requireNonNull(kind, "kind");
      String resourceName = Objects.requireNonNull(name, "name");
      Runnable resourceCleanup = Objects.requireNonNull(cleanup, "cleanup");
      RuntimeGenerationScope.Registration censusToken =
          generation == null ? null : generation.own(kind, resourceName);
      resources.addLast(new OwnedResource(resourceName, resourceCleanup, censusToken));
      return this;
    }

    public <T> RuntimeHandle<T> complete(T value) {
      if (completed) {
        throw new IllegalStateException("Runtime construction scope is already completed");
      }
      completed = true;
      RuntimeHandle<T> handle = new RuntimeHandle<>(value, resources, generation);
      resources.clear();
      return handle;
    }

    @Override
    public void close() {
      if (completed) {
        return;
      }
      completed = true;
      RuntimeHandle<Object> rollback = new RuntimeHandle<>(this, resources, generation);
      resources.clear();
      rollback.close();
    }
  }

  private record OwnedResource(
      String name, Runnable cleanup, RuntimeGenerationScope.Registration censusToken) {}
}
