package com.zxcmc.exort.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExortApiModelTest {
  @Test
  void contentIdsAreUniqueNormalizedAndRoundTrip() {
    var ids = new HashSet<String>();

    for (ExortContentType type : ExortContentType.values()) {
      assertTrue(ids.add(type.id()), () -> "duplicate content id: " + type.id());
      assertEquals(type.id(), type.id().trim().toLowerCase());
      assertEquals(
          type, ExortContentType.fromId("  " + type.id().toUpperCase() + "  ").orElseThrow());
    }

    assertTrue(ExortContentType.fromId(null).isEmpty());
    assertTrue(ExortContentType.fromId(" ").isEmpty());
    assertTrue(ExortContentType.fromId("foreign").isEmpty());
  }

  @Test
  void descriptorsDefensivelyValidatePublicValues() {
    ExortItemDescriptor unique =
        new ExortItemDescriptor(
            ExortContentType.STORAGE,
            Optional.of("  rare  "),
            ExortItemCopyPolicy.PRESERVE_UNIQUE_IDENTITY);

    assertEquals(Optional.of("rare"), unique.variantKey());
    assertTrue(unique.hasPersistentIdentity());
    assertFalse(
        new ExortItemDescriptor(
                ExortContentType.WIRE, Optional.empty(), ExortItemCopyPolicy.TEMPLATE)
            .hasPersistentIdentity());
    assertThrows(NullPointerException.class, () -> new ExortBlockDescriptor(null, false));
    assertThrows(
        NullPointerException.class,
        () -> new ExortItemDescriptor(ExortContentType.WIRE, null, ExortItemCopyPolicy.TEMPLATE));
  }
}
