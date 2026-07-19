package com.zxcmc.exort.integration.worldedit;

import java.util.Arrays;

record BusData(String type, String facing, String mode, byte[] filters) implements FacingOwner {
  BusData {
    filters = filters == null ? null : Arrays.copyOf(filters, filters.length);
  }

  @Override
  public byte[] filters() {
    return filters == null ? null : Arrays.copyOf(filters, filters.length);
  }

  int estimatedPayloadBytes() {
    return filters == null ? 0 : filters.length;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BusData data
        && java.util.Objects.equals(type, data.type)
        && java.util.Objects.equals(facing, data.facing)
        && java.util.Objects.equals(mode, data.mode)
        && Arrays.equals(filters, data.filters);
  }

  @Override
  public int hashCode() {
    int result = java.util.Objects.hash(type, facing, mode);
    return 31 * result + Arrays.hashCode(filters);
  }
}
