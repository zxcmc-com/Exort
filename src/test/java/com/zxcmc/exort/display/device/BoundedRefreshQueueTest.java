package com.zxcmc.exort.display.device;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedRefreshQueueTest {
  @Test
  void pollDrainsOnlyRequestedBudgetInInsertionOrder() {
    BoundedRefreshQueue<String> queue = new BoundedRefreshQueue<>();
    queue.enqueue("a");
    queue.enqueue("b");
    queue.enqueue("c");

    assertEquals(List.of("a", "b"), queue.poll(2));
    assertEquals(1, queue.size());
    assertEquals(List.of("c"), queue.poll(2));
  }

  @Test
  void enqueueDeduplicatesValues() {
    BoundedRefreshQueue<String> queue = new BoundedRefreshQueue<>();
    queue.enqueue("a");
    queue.enqueue("a");

    assertEquals(List.of("a"), queue.poll(10));
  }
}
