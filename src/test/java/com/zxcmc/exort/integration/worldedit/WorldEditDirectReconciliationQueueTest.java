package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldEditDirectReconciliationQueueTest {
  @Test
  void deduplicatesCoordinatesAndPreservesInsertionOrder() {
    WorldEditDirectReconciliationQueue queue = new WorldEditDirectReconciliationQueue(2);
    BlockRef first = new BlockRef(UUID.randomUUID(), 1, 2, 3);
    BlockRef second = new BlockRef(UUID.randomUUID(), 4, 5, 6);

    assertEquals(WorldEditDirectReconciliationQueue.ReserveResult.ADDED, queue.reserve(first));
    assertEquals(
        WorldEditDirectReconciliationQueue.ReserveResult.ALREADY_PRESENT, queue.reserve(first));
    assertEquals(WorldEditDirectReconciliationQueue.ReserveResult.ADDED, queue.reserve(second));
    assertEquals(2, queue.size());

    assertEquals(first, queue.poll());
    assertEquals(second, queue.poll());
    assertNull(queue.poll());
    assertTrue(queue.isEmpty());
  }

  @Test
  void rejectsNewCoordinatesAtCapacityAndCanBeCleared() {
    WorldEditDirectReconciliationQueue queue = new WorldEditDirectReconciliationQueue(1);
    BlockRef accepted = new BlockRef(UUID.randomUUID(), 1, 2, 3);
    BlockRef rejected = new BlockRef(UUID.randomUUID(), 4, 5, 6);

    assertEquals(WorldEditDirectReconciliationQueue.ReserveResult.ADDED, queue.reserve(accepted));
    assertEquals(WorldEditDirectReconciliationQueue.ReserveResult.FULL, queue.reserve(rejected));
    assertTrue(queue.contains(accepted));
    assertFalse(queue.contains(rejected));

    queue.clear();
    assertEquals(0, queue.size());
    assertTrue(queue.isEmpty());
  }
}
