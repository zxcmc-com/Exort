package com.zxcmc.exort.keys;

import java.util.UUID;

public final class PdcValueSanitizer {
  private PdcValueSanitizer() {}

  public static String uuidString(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value.trim()).toString();
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
