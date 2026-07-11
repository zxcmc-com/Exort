package com.zxcmc.exort.runtime;

import com.zxcmc.exort.i18n.ItemNameService;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Starts the asynchronous item-dictionary transition only after its runtime is published. */
public final class RuntimeItemNamesReload {
  private final Supplier<CompletableFuture<ItemNameService.Status>> starter;
  private CompletableFuture<ItemNameService.Status> future;

  public RuntimeItemNamesReload(Supplier<CompletableFuture<ItemNameService.Status>> starter) {
    this.starter = Objects.requireNonNull(starter, "starter");
  }

  public CompletableFuture<ItemNameService.Status> start() {
    CompletableFuture<ItemNameService.Status> shared;
    synchronized (this) {
      if (future != null) {
        return future;
      }
      shared = new CompletableFuture<>();
      future = shared;
    }
    try {
      Objects.requireNonNull(starter.get(), "item-name reload returned null")
          .whenComplete(
              (status, failure) -> {
                if (failure == null) {
                  shared.complete(status);
                } else {
                  shared.completeExceptionally(failure);
                }
              });
    } catch (RuntimeException | LinkageError failure) {
      shared.completeExceptionally(failure);
    }
    return shared;
  }
}
