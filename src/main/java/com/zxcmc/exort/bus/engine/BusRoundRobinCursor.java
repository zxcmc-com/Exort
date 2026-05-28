package com.zxcmc.exort.bus.engine;

final class BusRoundRobinCursor {
  private int index;

  int start(int size) {
    if (size <= 0) {
      index = 0;
      return 0;
    }
    index = Math.floorMod(index, size);
    return index;
  }

  void advance(int size) {
    if (size <= 0) {
      index = 0;
      return;
    }
    index = Math.floorMod(index + 1, size);
  }
}
