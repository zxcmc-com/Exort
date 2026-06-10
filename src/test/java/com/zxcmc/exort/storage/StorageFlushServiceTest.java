package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zxcmc.exort.infra.db.Database;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class StorageFlushServiceTest {
  @Test
  void snapshotPreparationFailureReturnsFailedFutureAndClearsInFlightFlush() {
    FailingSnapshotCache cache = new FailingSnapshotCache();
    CountingDatabase database = new CountingDatabase();
    StorageFlushService service =
        new StorageFlushService(Logger.getLogger("test"), () -> null, database);

    ExecutionException first =
        assertThrows(
            ExecutionException.class, () -> service.flushAsync(cache).get(5, TimeUnit.SECONDS));
    ExecutionException second =
        assertThrows(
            ExecutionException.class, () -> service.flushAsync(cache).get(5, TimeUnit.SECONDS));

    assertInstanceOf(IllegalStateException.class, first.getCause());
    assertInstanceOf(IllegalStateException.class, second.getCause());
    assertEquals(2, cache.snapshotAttempts);
    assertEquals(0, cache.markCleanAttempts);
    assertEquals(0, database.writeAttempts);
  }

  private static final class FailingSnapshotCache extends StorageCache {
    private int snapshotAttempts;
    private int markCleanAttempts;

    private FailingSnapshotCache() {
      super("storage", null, null, null);
    }

    @Override
    public synchronized boolean isDirty() {
      return true;
    }

    @Override
    public synchronized DeltaSnapshot snapshotDeltaWithVersion() {
      snapshotAttempts++;
      throw new IllegalStateException("cannot prepare snapshot");
    }

    @Override
    public synchronized void markCleanIfVersion(long versionAtSnapshot) {
      markCleanAttempts++;
    }
  }

  private static final class CountingDatabase extends Database {
    private int writeAttempts;

    @Override
    public java.util.concurrent.CompletableFuture<Void> writeDelta(
        String storageId,
        java.util.Collection<com.zxcmc.exort.infra.db.DbItem> upserts,
        java.util.Collection<String> removals) {
      writeAttempts++;
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> writeSnapshot(
        String storageId, java.util.Collection<com.zxcmc.exort.infra.db.DbItem> items) {
      writeAttempts++;
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
  }
}
