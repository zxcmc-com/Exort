package com.zxcmc.exort.wire.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WirePlacementLimitGuardTest {
  @Test
  void countsSingleAdjacentComponentWithNewTarget() {
    Map<Integer, List<Integer>> graph =
        Map.of(
            0, List.of(1),
            1, List.of(0, 2),
            2, List.of(1));
    Set<Integer> existing = Set.of(1, 2);

    int count =
        WirePlacementLimitGuard.mergedWireCount(
            0, node -> graph.getOrDefault(node, List.of()), existing::contains, node -> true, 64);

    assertEquals(3, count);
  }

  @Test
  void mergesMultipleAdjacentComponentsOnlyOnce() {
    Map<Integer, List<Integer>> graph =
        Map.of(
            0, List.of(1, 10),
            1, List.of(0, 2),
            2, List.of(1),
            10, List.of(0, 11),
            11, List.of(10));
    Set<Integer> existing = Set.of(1, 2, 10, 11);

    int count =
        WirePlacementLimitGuard.mergedWireCount(
            0, node -> graph.getOrDefault(node, List.of()), existing::contains, node -> true, 64);

    assertEquals(5, count);
  }

  @Test
  void stopsAfterHardCapIsExceeded() {
    Map<Integer, List<Integer>> graph =
        Map.of(
            0, List.of(1),
            1, List.of(0, 2),
            2, List.of(1, 3),
            3, List.of(2));
    Set<Integer> existing = Set.of(1, 2, 3);

    int count =
        WirePlacementLimitGuard.mergedWireCount(
            0, node -> graph.getOrDefault(node, List.of()), existing::contains, node -> true, 3);

    assertEquals(4, count);
  }

  @Test
  void ignoresUnloadedNeighbors() {
    Map<Integer, Collection<Integer>> graph =
        Map.of(
            0, List.of(1, 10),
            1, List.of(0),
            10, List.of(0, 11),
            11, List.of(10));
    Set<Integer> existing = Set.of(1, 10, 11);

    int count =
        WirePlacementLimitGuard.mergedWireCount(
            0,
            node -> graph.getOrDefault(node, List.of()),
            existing::contains,
            node -> node != 10,
            64);

    assertEquals(2, count);
  }
}
