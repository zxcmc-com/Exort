package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class ChunkLoaderLifecycleTest {
  @Test
  void stoppedGenerationCannotBecomeReadyAfterDelayedHydration() {
    ChunkLoaderLifecycle lifecycle = new ChunkLoaderLifecycle();
    ChunkLoaderLifecycle.Generation stopped = lifecycle.start();

    lifecycle.stop();

    assertFalse(lifecycle.markReady(stopped));
    assertFalse(lifecycle.isReady());
    assertTrue(lifecycle.readiness(stopped).isCompletedExceptionally());
  }

  @Test
  void delayedPreviousGenerationCannotCommitIntoRestartedLifecycle() {
    ChunkLoaderLifecycle lifecycle = new ChunkLoaderLifecycle();
    ChunkLoaderLifecycle.Generation previous = lifecycle.start();
    lifecycle.stop();
    ChunkLoaderLifecycle.Generation current = lifecycle.start();

    assertFalse(lifecycle.isActive(previous));
    assertFalse(lifecycle.markReady(previous));
    assertTrue(lifecycle.isActive(current));
    assertTrue(lifecycle.markReady(current));
    assertTrue(lifecycle.isReady());
    assertTrue(lifecycle.readiness(current).isDone());
  }

  @Test
  void hydrationFailureKeepsLifecycleClosedToMutations() {
    ChunkLoaderLifecycle lifecycle = new ChunkLoaderLifecycle();
    ChunkLoaderLifecycle.Generation generation = lifecycle.start();

    assertTrue(lifecycle.fail(generation, new CancellationException("database unavailable")));

    assertFalse(lifecycle.isReady());
    assertTrue(lifecycle.isActive(generation));
    assertTrue(lifecycle.readiness(generation).isCompletedExceptionally());
  }

  @Test
  void startupQuotaOrderPrefersOldestRecordsWithStableIdTieBreak() {
    ChunkLoaderRecord newer = record(3L, 200L);
    ChunkLoaderRecord olderSecond = record(2L, 100L);
    ChunkLoaderRecord olderFirst = record(1L, 100L);
    List<ChunkLoaderRecord> records = new ArrayList<>(List.of(newer, olderSecond, olderFirst));

    records.sort(ChunkLoaderService.startupOrder());

    assertEquals(List.of(olderFirst, olderSecond, newer), records);
  }

  private static ChunkLoaderRecord record(long id, long createdAt) {
    return new ChunkLoaderRecord(
        new UUID(0L, id),
        ChunkLoaderType.CHUNK_LOADER,
        new UUID(0L, 99L),
        "minecraft:overworld",
        "world",
        (int) id,
        64,
        0,
        0,
        0,
        new UUID(0L, 10L),
        "Alex",
        1,
        true,
        createdAt,
        createdAt);
  }
}
