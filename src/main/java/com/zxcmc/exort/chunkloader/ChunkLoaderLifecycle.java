package com.zxcmc.exort.chunkloader;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/** Guards asynchronous Chunk Loader hydration from committing into a stopped runtime. */
final class ChunkLoaderLifecycle {
  private long nextGeneration;
  private Generation current;
  private CompletableFuture<Void> readyFuture = new CompletableFuture<>();
  private boolean active;
  private boolean ready;

  synchronized Generation start() {
    if (active) {
      return current;
    }
    current = new Generation(++nextGeneration);
    readyFuture = new CompletableFuture<>();
    active = true;
    ready = false;
    return current;
  }

  synchronized void stop() {
    if (!active) {
      return;
    }
    active = false;
    ready = false;
    readyFuture.completeExceptionally(new CancellationException("Chunk Loader runtime stopped"));
  }

  synchronized boolean isActive(Generation generation) {
    return active && Objects.equals(current, generation);
  }

  synchronized boolean markReady(Generation generation) {
    if (!isActive(generation) || readyFuture.isDone()) {
      return false;
    }
    ready = true;
    readyFuture.complete(null);
    return true;
  }

  synchronized boolean fail(Generation generation, Throwable failure) {
    if (!isActive(generation) || readyFuture.isDone()) {
      return false;
    }
    ready = false;
    readyFuture.completeExceptionally(Objects.requireNonNull(failure, "failure"));
    return true;
  }

  synchronized boolean isReady() {
    return active && ready;
  }

  synchronized CompletableFuture<Void> readiness(Generation generation) {
    if (Objects.equals(current, generation)) {
      return readyFuture.copy();
    }
    return CompletableFuture.failedFuture(
        new CancellationException("Chunk Loader runtime generation is obsolete"));
  }

  record Generation(long value) {}
}
