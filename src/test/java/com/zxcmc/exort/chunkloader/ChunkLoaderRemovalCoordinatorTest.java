package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChunkLoaderRemovalCoordinatorTest {
  @Test
  void failedDeleteLeavesMutationUncommittedAndAllowsRetry() {
    ChunkLoaderRemovalCoordinator coordinator = new ChunkLoaderRemovalCoordinator(Runnable::run);
    ChunkLoaderRemovalCoordinator.Key key =
        ChunkLoaderRemovalCoordinator.Key.loader(new UUID(0L, 1L), new UUID(0L, 101L), 1, 64, 1);
    CompletableFuture<Void> failedDelete = new CompletableFuture<>();
    AtomicInteger commits = new AtomicInteger();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    assertTrue(coordinator.start(key, () -> failedDelete, commits::incrementAndGet, failure::set));
    IOException injected = new IOException("injected delete failure");
    failedDelete.completeExceptionally(injected);

    assertEquals(0, commits.get());
    assertEquals(injected, failure.get());
    assertFalse(coordinator.isPending(key));

    assertTrue(
        coordinator.start(
            key,
            () -> CompletableFuture.completedFuture(null),
            commits::incrementAndGet,
            failure::set));
    assertEquals(1, commits.get());
  }

  @Test
  void duplicateBreakWhileDeleteIsPendingCannotCreateSecondCommit() {
    ChunkLoaderRemovalCoordinator coordinator = new ChunkLoaderRemovalCoordinator(Runnable::run);
    ChunkLoaderRemovalCoordinator.Key key =
        ChunkLoaderRemovalCoordinator.Key.loader(new UUID(0L, 2L), new UUID(0L, 102L), 2, 64, 2);
    CompletableFuture<Void> delete = new CompletableFuture<>();
    AtomicInteger deleteRequests = new AtomicInteger();
    AtomicInteger firstCommits = new AtomicInteger();
    AtomicInteger duplicateCommits = new AtomicInteger();

    assertTrue(
        coordinator.start(
            key,
            () -> {
              deleteRequests.incrementAndGet();
              return delete;
            },
            firstCommits::incrementAndGet,
            ignored -> {}));
    assertFalse(
        coordinator.start(
            key,
            () -> {
              deleteRequests.incrementAndGet();
              return CompletableFuture.completedFuture(null);
            },
            duplicateCommits::incrementAndGet,
            ignored -> {}));

    delete.complete(null);

    assertEquals(1, firstCommits.get());
    assertEquals(0, duplicateCommits.get());
    assertEquals(1, deleteRequests.get());
  }

  @Test
  void cancellationMakesLateDeleteCallbackInert() {
    ChunkLoaderRemovalCoordinator coordinator = new ChunkLoaderRemovalCoordinator(Runnable::run);
    ChunkLoaderRemovalCoordinator.Key key =
        ChunkLoaderRemovalCoordinator.Key.loader(new UUID(0L, 3L), new UUID(0L, 103L), 3, 64, 3);
    CompletableFuture<Void> delete = new CompletableFuture<>();
    AtomicInteger commits = new AtomicInteger();

    assertTrue(coordinator.start(key, () -> delete, commits::incrementAndGet, ignored -> {}));
    coordinator.cancel(key);
    delete.complete(null);

    assertEquals(0, commits.get());
  }

  @Test
  void destinationReconcileSupersedesPendingSourceRemovalByLoaderId() {
    ChunkLoaderRemovalCoordinator coordinator = new ChunkLoaderRemovalCoordinator(Runnable::run);
    UUID loaderId = new UUID(0L, 4L);
    UUID worldId = new UUID(0L, 104L);
    ChunkLoaderRemovalCoordinator.Key source =
        ChunkLoaderRemovalCoordinator.Key.loader(loaderId, worldId, 4, 64, 4);
    ChunkLoaderRemovalCoordinator.Key destination =
        ChunkLoaderRemovalCoordinator.Key.loader(loaderId, worldId, 14, 64, 14);
    CompletableFuture<Void> sourceDelete = new CompletableFuture<>();
    AtomicInteger sourceCommits = new AtomicInteger();

    assertTrue(
        coordinator.start(
            source, () -> sourceDelete, sourceCommits::incrementAndGet, ignored -> {}));
    assertEquals(source, coordinator.cancelByLoaderId(loaderId));
    assertFalse(coordinator.isPending(source));

    AtomicInteger destinationDeleteRequests = new AtomicInteger();
    assertTrue(
        coordinator.start(
            destination,
            () -> {
              destinationDeleteRequests.incrementAndGet();
              return CompletableFuture.completedFuture(null);
            },
            () -> {},
            ignored -> {}));
    sourceDelete.complete(null);

    assertEquals(0, sourceCommits.get());
    assertEquals(1, destinationDeleteRequests.get());
  }
}
