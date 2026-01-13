package com.zxcmc.exort.bus;

public enum BusType {
  IMPORT,
  EXPORT;

  public static BusType fromString(String raw) {
    if (raw == null) return null;
    try {
      return BusType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
