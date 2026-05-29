package com.zxcmc.exort.display;

import java.util.Locale;

public enum WireRenderMode {
  AUTO,
  COMPACT,
  DETAILED;

  public static WireRenderMode fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return AUTO;
    }
    try {
      return WireRenderMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return AUTO;
    }
  }
}
