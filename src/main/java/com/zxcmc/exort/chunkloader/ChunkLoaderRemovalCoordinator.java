package com.zxcmc.exort.chunkloader;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Main-thread-confined coordinator for delete-before-mutate Chunk Loader removals. */
final class ChunkLoaderRemovalCoordinator {
  record Key(UUID loaderId, UUID worldId, int x, int y, int z) {
    static Key loader(UUID loaderId, UUID worldId, int x, int y, int z) {
      return new Key(
          Objects.requireNonNull(loaderId, "loaderId"),
          Objects.requireNonNull(worldId, "worldId"),
          x,
          y,
          z);
    }

    static Key block(UUID worldId, int x, int y, int z) {
      return new Key(null, Objects.requireNonNull(worldId, "worldId"), x, y, z);
    }
  }

  private final Consumer<Runnable> mainThreadDispatcher;
  private final Map<Key, Long> pending = new HashMap<>();
  private final Map<UUID, Key> pendingByLoaderId = new HashMap<>();
  private long nextOperation;

  ChunkLoaderRemovalCoordinator(Consumer<Runnable> mainThreadDispatcher) {
    this.mainThreadDispatcher =
        Objects.requireNonNull(mainThreadDispatcher, "mainThreadDispatcher");
  }

  boolean start(
      Key key,
      Supplier<? extends CompletionStage<Void>> deletion,
      Runnable commit,
      Consumer<Throwable> failure) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(deletion, "deletion");
    Objects.requireNonNull(commit, "commit");
    Objects.requireNonNull(failure, "failure");
    if (pending.containsKey(key)
        || (key.loaderId() != null && pendingByLoaderId.containsKey(key.loaderId()))) {
      return false;
    }
    long operation = ++nextOperation;
    pending.put(key, operation);
    if (key.loaderId() != null) {
      pendingByLoaderId.put(key.loaderId(), key);
    }
    CompletionStage<Void> deletionStage;
    try {
      deletionStage = Objects.requireNonNull(deletion.get(), "deletion stage");
    } catch (RuntimeException error) {
      remove(key, operation);
      failure.accept(error);
      return true;
    }
    deletionStage.whenComplete(
        (ignored, error) ->
            mainThreadDispatcher.accept(
                () -> {
                  if (!remove(key, operation)) {
                    return;
                  }
                  if (error != null) {
                    failure.accept(error);
                    return;
                  }
                  commit.run();
                }));
    return true;
  }

  boolean isPending(Key key) {
    return key != null && pending.containsKey(key);
  }

  boolean cancel(Key key) {
    if (key == null) {
      return false;
    }
    Long operation = pending.get(key);
    return operation != null && remove(key, operation);
  }

  Key cancelByLoaderId(UUID loaderId) {
    Key key = loaderId == null ? null : pendingByLoaderId.get(loaderId);
    return cancel(key) ? key : null;
  }

  void clear() {
    pending.clear();
    pendingByLoaderId.clear();
  }

  private boolean remove(Key key, long operation) {
    if (!pending.remove(key, operation)) {
      return false;
    }
    if (key.loaderId() != null) {
      pendingByLoaderId.remove(key.loaderId(), key);
    }
    return true;
  }
}
