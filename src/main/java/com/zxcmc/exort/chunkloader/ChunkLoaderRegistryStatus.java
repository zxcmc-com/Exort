package com.zxcmc.exort.chunkloader;

import java.util.Locale;

public enum ChunkLoaderRegistryStatus {
  ACTIVE,
  ITEM,
  LOST,
  REMOVED;

  public String dbValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public boolean isObserved() {
    return this == ACTIVE || this == ITEM;
  }

  public static ChunkLoaderRegistryStatus fromDb(String raw) {
    if (raw == null || raw.isBlank()) {
      return LOST;
    }
    try {
      return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return LOST;
    }
  }
}
