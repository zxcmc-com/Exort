package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DisplayClassificationTest {
  @Test
  void wireDisplayRequiresManagedDisplayAndWireRole() {
    assertTrue(
        DisplayClassification.isAdoptableWireDisplay(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.WIRE_COMPACT_TAG)));
    assertFalse(DisplayClassification.isAdoptableWireDisplay(Set.of(DisplayTags.WIRE_COMPACT_TAG)));
    assertFalse(DisplayClassification.isAdoptableWireDisplay(Set.of(DisplayTags.DISPLAY_TAG)));
  }

  @Test
  void breakOverlaysAndHologramsAreNeverAdoptableWireDisplays() {
    assertFalse(
        DisplayClassification.isAdoptableWireDisplay(
            Set.of(
                DisplayTags.DISPLAY_TAG,
                DisplayTags.WIRE_COMPACT_TAG,
                DisplayTags.BREAK_OVERLAY_TAG)));
    assertFalse(
        DisplayClassification.isAdoptableWireDisplay(
            Set.of(
                DisplayTags.DISPLAY_TAG, DisplayTags.WIRE_COMPACT_TAG, DisplayTags.HOLOGRAM_TAG)));
  }
}
