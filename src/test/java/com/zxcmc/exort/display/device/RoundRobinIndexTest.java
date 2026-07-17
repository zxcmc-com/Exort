package com.zxcmc.exort.display.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoundRobinIndexTest {
  @Test
  void largePopulationReturnsOnlyBoundedFairBatches() {
    RoundRobinIndex<Integer> index = new RoundRobinIndex<>();
    for (int value = 0; value < 5_000; value++) {
      assertTrue(index.add(value));
    }
    Set<Integer> seen = new HashSet<>();

    for (int batch = 0; batch < 313; batch++) {
      var values = index.nextBatch(16);
      assertEquals(16, values.size());
      seen.addAll(values);
    }
    seen.addAll(index.nextBatch(16));

    assertEquals(5_000, seen.size());
    assertEquals(5_000, index.size());
  }

  @Test
  void removalBeforeCursorDoesNotSkipRemainingEntries() {
    RoundRobinIndex<Integer> index = new RoundRobinIndex<>();
    for (int value = 0; value < 6; value++) {
      index.add(value);
    }

    assertEquals(java.util.List.of(0, 1, 2), index.nextBatch(3));
    assertTrue(index.remove(1));
    assertFalse(index.remove(1));
    assertEquals(java.util.List.of(3, 4, 5), index.nextBatch(3));
    assertEquals(java.util.List.of(0, 2, 3), index.nextBatch(3));
  }
}
