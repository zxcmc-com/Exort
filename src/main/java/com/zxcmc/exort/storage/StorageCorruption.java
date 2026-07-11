package com.zxcmc.exort.storage;

import java.util.Objects;

/** A corrupt persisted item row that makes its owning storage read-only. */
public record StorageCorruption(String itemKey, long amount, String reason, long detectedAt) {
  public StorageCorruption {
    itemKey = itemKey == null ? "<null>" : itemKey;
    reason = Objects.requireNonNull(reason, "reason");
  }
}
