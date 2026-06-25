package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChorusFaceMaskTest {
  @Test
  void parsesNamedFaces() {
    ChorusFaceMask mask = ChorusFaceMask.parse("north,east,up");
    assertTrue(mask.north());
    assertTrue(mask.east());
    assertTrue(mask.up());
    assertFalse(mask.south());
    assertFalse(mask.west());
    assertFalse(mask.down());
  }

  @Test
  void parsesBinaryMasksInDocumentedOrder() {
    assertEquals(
        new ChorusFaceMask(true, false, true, false, true, false), ChorusFaceMask.parse("101010"));
  }

  @Test
  void detectsImpossibleCarrierMask() {
    assertTrue(ChorusFaceMask.parse("up,down,north").isImpossibleCustomCarrier());
    assertTrue(ChorusFaceMask.ALL.isImpossibleCustomCarrier());
    assertFalse(ChorusFaceMask.parse("up,down").isImpossibleCustomCarrier());
    assertFalse(ChorusFaceMask.parse("down,north").isImpossibleCustomCarrier());
  }

  @Test
  void rejectsUnknownFaceToken() {
    assertThrows(IllegalArgumentException.class, () -> ChorusFaceMask.parse("north,sideways"));
  }
}
