package com.zxcmc.exort.wireless.transmitter;

import java.util.Locale;

public enum TransmitterMode {
  CHARGE_ONLY,
  BIND,
  DISABLED;

  public TransmitterMode next() {
    return switch (this) {
      case CHARGE_ONLY -> BIND;
      case BIND -> DISABLED;
      case DISABLED -> CHARGE_ONLY;
    };
  }

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static TransmitterMode fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return CHARGE_ONLY;
    }
    if ("charge".equalsIgnoreCase(raw.trim())) {
      return CHARGE_ONLY;
    }
    try {
      return TransmitterMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return CHARGE_ONLY;
    }
  }
}
