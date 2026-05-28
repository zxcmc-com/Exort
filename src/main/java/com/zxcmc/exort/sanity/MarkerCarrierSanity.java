package com.zxcmc.exort.sanity;

final class MarkerCarrierSanity {
  private MarkerCarrierSanity() {}

  static boolean validOrMigratableCarrier(
      boolean matchesAnyConfiguredCarrier,
      boolean hadWire,
      boolean isBarrier,
      boolean isFullChorus) {
    return matchesAnyConfiguredCarrier || isMigratableWireCarrier(hadWire, isBarrier, isFullChorus);
  }

  static boolean isMigratableWireCarrier(boolean hadWire, boolean isBarrier, boolean isFullChorus) {
    return hadWire && (isBarrier || isFullChorus);
  }
}
