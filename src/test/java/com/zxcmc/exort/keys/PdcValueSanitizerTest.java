package com.zxcmc.exort.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PdcValueSanitizerTest {
  @Test
  void acceptsCanonicalUuidStrings() {
    assertEquals(
        "00000000-0000-0000-0000-000000000001",
        PdcValueSanitizer.uuidString("00000000-0000-0000-0000-000000000001"));
  }

  @Test
  void rejectsInvalidUuidStrings() {
    assertNull(PdcValueSanitizer.uuidString(null));
    assertNull(PdcValueSanitizer.uuidString(""));
    assertNull(PdcValueSanitizer.uuidString("not-a-uuid"));
  }
}
