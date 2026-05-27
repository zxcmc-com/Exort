package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuiPageWindowTest {
  @Test
  void emptySlotListStillHasOnePage() {
    GuiPageWindow window = GuiPageWindow.forSlots(0, 0, 45);

    assertEquals(0, window.page());
    assertEquals(1, window.displayPage());
    assertEquals(1, window.totalPages());
    assertEquals(0, window.startIndex());
    assertFalse(window.hasNext());
  }

  @Test
  void clampsPageIntoAvailableRange() {
    GuiPageWindow high = GuiPageWindow.forSlots(9, 91, 45);
    GuiPageWindow low = GuiPageWindow.forSlots(-3, 91, 45);

    assertEquals(2, high.page());
    assertEquals(3, high.totalPages());
    assertEquals(90, high.startIndex());
    assertFalse(high.hasNext());
    assertEquals(0, low.page());
    assertTrue(low.hasNext());
  }

  @Test
  void rejectsInvalidInputs() {
    assertThrows(IllegalArgumentException.class, () -> GuiPageWindow.forSlots(0, -1, 45));
    assertThrows(IllegalArgumentException.class, () -> GuiPageWindow.forSlots(0, 1, 0));
  }
}
