package com.zxcmc.exort.display.culling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoundRobinWorkQueueTest {
  @Test
  void oneHundredPlayersReceiveWorkWithoutStarvation() {
    RoundRobinWorkQueue<Integer> queue = new RoundRobinWorkQueue<>();
    for (int player = 0; player < 100; player++) {
      queue.add(player);
    }
    Set<Integer> firstRound = new HashSet<>();
    for (int slice = 0; slice < 100; slice++) {
      firstRound.add(queue.next());
    }

    assertEquals(100, firstRound.size());
    assertEquals(0, queue.next());
    queue.remove(0);
    assertEquals(1, queue.next());
  }
}
