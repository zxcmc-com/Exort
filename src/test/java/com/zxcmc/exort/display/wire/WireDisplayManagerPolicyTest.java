package com.zxcmc.exort.display.wire;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.display.core.DisplayTags;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WireDisplayManagerPolicyTest {
  @Test
  void reusableWireDisplayRequiresGenericAndWireTags() {
    assertTrue(
        WireDisplayManager.isWireDisplayTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.WIRE_COMPACT_TAG)));

    assertFalse(WireDisplayManager.isWireDisplayTags(Set.of(DisplayTags.DISPLAY_TAG)));
    assertFalse(
        WireDisplayManager.isWireDisplayTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.HOLOGRAM_TAG)));
    assertFalse(WireDisplayManager.isWireDisplayTags(Set.of(DisplayTags.WIRE_COMPACT_TAG)));
  }
}
