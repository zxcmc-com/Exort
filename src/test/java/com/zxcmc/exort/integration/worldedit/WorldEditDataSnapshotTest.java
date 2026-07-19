package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class WorldEditDataSnapshotTest {
  @Test
  void busDataDefensivelyCopiesFilters() {
    byte[] filters = {1, 2, 3};
    BusData data = new BusData("import", "north", "whitelist", filters);

    filters[0] = 9;
    byte[] returned = data.filters();
    returned[1] = 8;

    assertArrayEquals(new byte[] {1, 2, 3}, data.filters());
    assertEquals(data, new BusData("import", "north", "whitelist", new byte[] {1, 2, 3}));
    assertNotEquals(data, new BusData("import", "north", "whitelist", new byte[] {1, 2}));
  }

  @Test
  void monitorDataDefensivelyCopiesItemBlob() {
    byte[] blob = {4, 5, 6};
    MonitorData data = new MonitorData("south", "key", blob);

    blob[0] = 0;
    byte[] returned = data.itemBlob();
    returned[1] = 0;

    assertArrayEquals(new byte[] {4, 5, 6}, data.itemBlob());
    assertEquals(data, new MonitorData("south", "key", new byte[] {4, 5, 6}));
    assertNotEquals(data, new MonitorData("south", "key", new byte[] {4, 5}));
  }
}
