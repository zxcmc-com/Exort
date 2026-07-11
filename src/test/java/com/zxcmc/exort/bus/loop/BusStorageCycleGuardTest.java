package com.zxcmc.exort.bus.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusStorageCycleGuardTest {
  @Test
  void disablesEveryBusInTwoStorageCycle() {
    BusPos first = pos(1);
    BusPos second = pos(2);

    Set<BusPos> cyclic =
        BusStorageCycleGuard.cyclicBuses(
            List.of(
                edge(first, "storage-a", "storage-b", BusMode.ALL, Set.of()),
                edge(second, "storage-b", "storage-a", BusMode.ALL, Set.of())));

    assertEquals(Set.of(first, second), cyclic);
  }

  @Test
  void detectsThreeStorageCycleButNotAcyclicTail() {
    BusPos first = pos(1);
    BusPos second = pos(2);
    BusPos third = pos(3);
    BusPos tail = pos(4);

    Set<BusPos> cyclic =
        BusStorageCycleGuard.cyclicBuses(
            List.of(
                edge(first, "storage-a", "storage-b", BusMode.ALL, Set.of()),
                edge(second, "storage-b", "storage-c", BusMode.ALL, Set.of()),
                edge(third, "storage-c", "storage-a", BusMode.ALL, Set.of()),
                edge(tail, "storage-c", "storage-d", BusMode.ALL, Set.of())));

    assertEquals(Set.of(first, second, third), cyclic);
  }

  @Test
  void nonOverlappingWhitelistCycleRemainsEnabled() {
    Set<BusPos> cyclic =
        BusStorageCycleGuard.cyclicBuses(
            List.of(
                edge(
                    pos(1),
                    "storage-a",
                    "storage-b",
                    BusMode.WHITELIST,
                    Set.of("minecraft:iron_ingot")),
                edge(
                    pos(2),
                    "storage-b",
                    "storage-a",
                    BusMode.WHITELIST,
                    Set.of("minecraft:gold_ingot"))));

    assertTrue(cyclic.isEmpty());
  }

  @Test
  void blacklistAndWhitelistCycleRequiresAnAllowedSharedItem() {
    BusPos blacklist = pos(1);
    BusPos whitelist = pos(2);

    assertTrue(
        BusStorageCycleGuard.cyclicBuses(
                List.of(
                    edge(
                        blacklist,
                        "storage-a",
                        "storage-b",
                        BusMode.BLACKLIST,
                        Set.of("minecraft:iron_ingot")),
                    edge(
                        whitelist,
                        "storage-b",
                        "storage-a",
                        BusMode.WHITELIST,
                        Set.of("minecraft:iron_ingot"))))
            .isEmpty());

    assertEquals(
        Set.of(blacklist, whitelist),
        BusStorageCycleGuard.cyclicBuses(
            List.of(
                edge(
                    blacklist,
                    "storage-a",
                    "storage-b",
                    BusMode.BLACKLIST,
                    Set.of("minecraft:gold_ingot")),
                edge(
                    whitelist,
                    "storage-b",
                    "storage-a",
                    BusMode.WHITELIST,
                    Set.of("minecraft:iron_ingot")))));
  }

  @Test
  void handlesTheAcceptedFiveHundredBusGraphWithoutDroppingCycleMembers() {
    List<BusStorageCycleGuard.Edge> edges = new ArrayList<>();
    for (int index = 0; index < 500; index++) {
      edges.add(
          edge(
              pos(index),
              "storage-" + index,
              "storage-" + ((index + 1) % 500),
              BusMode.ALL,
              Set.of()));
    }

    Set<BusPos> cyclic = BusStorageCycleGuard.cyclicBuses(edges);

    assertEquals(500, cyclic.size());
  }

  private static BusStorageCycleGuard.Edge edge(
      BusPos bus, String from, String to, BusMode mode, Set<String> filters) {
    return new BusStorageCycleGuard.Edge(bus, from, to, mode, filters);
  }

  private static BusPos pos(int x) {
    return new BusPos(new UUID(0L, 1L), x, 64, 0);
  }
}
