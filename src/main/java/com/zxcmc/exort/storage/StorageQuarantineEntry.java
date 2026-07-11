package com.zxcmc.exort.storage;

import java.util.Arrays;
import java.util.Objects;

/** A defensive copy of a corrupt row's original persistent representation. */
public record StorageQuarantineEntry(StorageCorruption corruption, byte[] originalBlob) {
  public StorageQuarantineEntry {
    Objects.requireNonNull(corruption, "corruption");
    originalBlob = originalBlob == null ? null : Arrays.copyOf(originalBlob, originalBlob.length);
  }

  @Override
  public byte[] originalBlob() {
    return originalBlob == null ? null : Arrays.copyOf(originalBlob, originalBlob.length);
  }
}
