package com.zxcmc.exort.integration.worldedit;

import java.util.Arrays;

record MonitorData(String facing, String itemKey, byte[] itemBlob) implements FacingOwner {
  MonitorData {
    itemBlob = itemBlob == null ? null : Arrays.copyOf(itemBlob, itemBlob.length);
  }

  @Override
  public byte[] itemBlob() {
    return itemBlob == null ? null : Arrays.copyOf(itemBlob, itemBlob.length);
  }

  int estimatedPayloadBytes() {
    return itemBlob == null ? 0 : itemBlob.length;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof MonitorData data
        && java.util.Objects.equals(facing, data.facing)
        && java.util.Objects.equals(itemKey, data.itemKey)
        && Arrays.equals(itemBlob, data.itemBlob);
  }

  @Override
  public int hashCode() {
    int result = java.util.Objects.hash(facing, itemKey);
    return 31 * result + Arrays.hashCode(itemBlob);
  }
}
