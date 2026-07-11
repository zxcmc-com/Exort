package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkLoaderPersistenceGuardTest {
  @Test
  void staleFailureCannotRollbackNewerMutation() {
    ChunkLoaderPersistenceGuard guard = new ChunkLoaderPersistenceGuard();
    UUID loaderId = new UUID(0L, 1L);
    long previous = guard.begin(loaderId);
    long current = guard.begin(loaderId);

    assertFalse(guard.complete(loaderId, previous));
    assertTrue(guard.complete(loaderId, current));
  }

  @Test
  void cancellationPreventsDelayedCallbackFromMutatingRemovedLoader() {
    ChunkLoaderPersistenceGuard guard = new ChunkLoaderPersistenceGuard();
    UUID loaderId = new UUID(0L, 2L);
    long operation = guard.begin(loaderId);

    guard.cancel(loaderId);

    assertFalse(guard.complete(loaderId, operation));
  }
}
