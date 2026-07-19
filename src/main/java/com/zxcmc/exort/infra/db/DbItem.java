package com.zxcmc.exort.infra.db;

import java.util.Arrays;

/** Immutable serialized item row used across the asynchronous database boundary. */
public record DbItem(String key, byte[] blob, long amount) {
  public DbItem {
    blob = blob == null ? null : Arrays.copyOf(blob, blob.length);
  }

  @Override
  public byte[] blob() {
    return blob == null ? null : Arrays.copyOf(blob, blob.length);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DbItem item
        && amount == item.amount
        && java.util.Objects.equals(key, item.key)
        && Arrays.equals(blob, item.blob);
  }

  @Override
  public int hashCode() {
    int result = java.util.Objects.hash(key, amount);
    return 31 * result + Arrays.hashCode(blob);
  }
}
