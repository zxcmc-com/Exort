package com.zxcmc.exort.bus.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BusRoundRobinCursorTest {
  @Test
  void advancesOneStartPositionPerTickWhenOperationBudgetIsOne() {
    BusRoundRobinCursor cursor = new BusRoundRobinCursor();

    assertEquals(0, cursor.start(4));
    cursor.advance(4);
    assertEquals(1, cursor.start(4));
    cursor.advance(4);
    assertEquals(2, cursor.start(4));
    cursor.advance(4);
    assertEquals(3, cursor.start(4));
    cursor.advance(4);
    assertEquals(0, cursor.start(4));
  }

  @Test
  void clampsWhenBusCountShrinks() {
    BusRoundRobinCursor cursor = new BusRoundRobinCursor();

    cursor.advance(4);
    cursor.advance(4);
    cursor.advance(4);

    assertEquals(0, cursor.start(1));
  }
}
