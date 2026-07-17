package com.zxcmc.exort.display.refresh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class NetworkRefreshWorkQueueTest {
  @Test
  void twentyThousandDuplicateStartsTraverseOneComponentWithinPerTickBudget() {
    TestTopology topology = TestTopology.chain(20_000);
    NetworkRefreshWorkQueue<Integer> queue = new NetworkRefreshWorkQueue<>();
    long batch = queue.openBatch();
    for (int node = 0; node < 20_000; node++) {
      queue.addStart(batch, node);
    }
    queue.seal(batch);
    int componentsStarted = 0;
    int componentsCompleted = 0;

    while (queue.hasSealedWork()) {
      var result = queue.drain(2_048, 20_000, topology);
      assertTrue(result.examined() <= 2_048);
      assertTrue(result.refreshes() <= 2_048);
      componentsStarted += result.componentsStarted();
      componentsCompleted += result.componentsCompleted();
    }

    assertEquals(1, componentsStarted);
    assertEquals(1, componentsCompleted);
    assertEquals(20_000, topology.refreshed.size());
  }

  @Test
  void splitStartRefreshesEachConnectedComponentOnce() {
    TestTopology topology = new TestTopology();
    topology.connect(1, 2);
    topology.connect(3, 4);
    topology.connect(5, 6);
    topology.connect(0, 1);
    topology.connect(0, 3);
    topology.connect(0, 5);
    topology.nodes.remove(0);
    NetworkRefreshWorkQueue<Integer> queue = new NetworkRefreshWorkQueue<>();
    long batch = queue.openBatch();
    queue.addStart(batch, 0);
    queue.addStart(batch, 2);
    queue.addStart(batch, 4);
    queue.addStart(batch, 6);
    queue.seal(batch);
    int started = 0;

    while (queue.hasSealedWork()) {
      started += queue.drain(3, 64, topology).componentsStarted();
    }

    assertEquals(3, started);
    assertEquals(Set.of(1, 2, 3, 4, 5, 6), topology.refreshed);
  }

  @Test
  void unsealedBatchWaitsAndHardCapAbortsRemainingStarts() {
    TestTopology topology = TestTopology.chain(100);
    NetworkRefreshWorkQueue<Integer> queue = new NetworkRefreshWorkQueue<>();
    long batch = queue.openBatch();
    queue.addStart(batch, 0);

    assertFalse(queue.hasSealedWork());
    assertEquals(0, queue.drain(2_048, 16, topology).examined());
    assertEquals(1, queue.pendingStarts());
    queue.seal(batch);
    int overflows = 0;
    while (queue.hasSealedWork()) {
      overflows += queue.drain(2_048, 16, topology).overflowedBatches();
    }

    assertEquals(1, overflows);
    assertEquals(16, topology.refreshed.size());
    assertEquals(0, queue.pendingStarts());
  }

  @Test
  void sealedBatchRejectsLateStartsAndClearDropsLifecycleState() {
    TestTopology topology = TestTopology.chain(8);
    NetworkRefreshWorkQueue<Integer> queue = new NetworkRefreshWorkQueue<>();
    long batch = queue.openBatch();
    queue.addStart(batch, 0);
    queue.addStart(batch, 7);
    assertTrue(queue.seal(batch));
    assertFalse(queue.addStart(batch, 3));

    queue.clear();

    assertFalse(queue.hasSealedWork());
    assertEquals(0, queue.pendingStarts());
    assertEquals(0, queue.pendingWork());
    assertEquals(0, queue.drain(32, 8, topology).examined());
  }

  private static final class TestTopology implements NetworkRefreshWorkQueue.Topology<Integer> {
    private final Set<Integer> nodes = new HashSet<>();
    private final Map<Integer, List<Integer>> neighbors = new HashMap<>();
    private final Set<Integer> refreshed = new HashSet<>();

    private static TestTopology chain(int size) {
      TestTopology topology = new TestTopology();
      for (int node = 0; node < size; node++) {
        topology.nodes.add(node);
        if (node > 0) {
          topology.connect(node - 1, node);
        }
      }
      return topology;
    }

    private void connect(int first, int second) {
      nodes.add(first);
      nodes.add(second);
      neighbors.computeIfAbsent(first, ignored -> new ArrayList<>()).add(second);
      neighbors.computeIfAbsent(second, ignored -> new ArrayList<>()).add(first);
    }

    @Override
    public boolean isNode(Integer node) {
      return nodes.contains(node);
    }

    @Override
    public void forEachConnectedNode(Integer node, Consumer<Integer> consumer) {
      for (Integer neighbor : neighbors.getOrDefault(node, List.of())) {
        if (nodes.contains(neighbor)) {
          consumer.accept(neighbor);
        }
      }
    }

    @Override
    public void enqueueRefresh(Integer node) {
      refreshed.add(node);
    }
  }
}
