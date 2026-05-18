package com.zxcmc.exort.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class BusFilterCodecTest {
  @Test
  void decodeRejectsNegativeSlotCount() {
    byte[] blob = ByteBuffer.allocate(Integer.BYTES).putInt(-1).array();

    var decoded = BusFilterCodec.decode(blob, 10);

    assertEquals(10, decoded.length);
    for (var item : decoded) {
      assertNull(item);
    }
  }

  @Test
  void decodeRejectsOversizedSlotCount() {
    byte[] blob = ByteBuffer.allocate(Integer.BYTES).putInt(55).array();

    var decoded = BusFilterCodec.decode(blob, 10);

    assertEquals(10, decoded.length);
    for (var item : decoded) {
      assertNull(item);
    }
  }

  @Test
  void decodeStopsBeforeDeclaredLengthPastBlobEnd() {
    byte[] blob = ByteBuffer.allocate(Integer.BYTES * 2).putInt(1).putInt(128).array();

    var decoded = BusFilterCodec.decode(blob, 10);

    assertEquals(10, decoded.length);
    assertNull(decoded[0]);
  }

  @Test
  void decodeClampsRequestedSlotCount() {
    byte[] blob = ByteBuffer.allocate(Integer.BYTES).putInt(0).array();

    var decoded = BusFilterCodec.decode(blob, 10_000);

    assertEquals(54, decoded.length);
  }
}
