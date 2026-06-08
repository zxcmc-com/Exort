package com.zxcmc.exort.platform;

import com.zxcmc.exort.carrier.WireCarrierMode;
import java.util.Locale;

public record ModePolicy(
    String configuredMode,
    boolean resourceMode,
    boolean resourceWireUsesBarrier,
    boolean resourceWireCarrierFallback,
    boolean unknownMode) {
  public static final String DEFAULT_MODE = "RESOURCE";

  public static ModePolicy evaluate(String rawMode, boolean chorusUpdatesDisabled) {
    return evaluate(rawMode, chorusUpdatesDisabled, WireCarrierMode.DEFAULT);
  }

  public static ModePolicy evaluate(
      String rawMode, boolean chorusUpdatesDisabled, WireCarrierMode wireCarrierMode) {
    String configured =
        rawMode == null || rawMode.isBlank() ? DEFAULT_MODE : rawMode.toUpperCase(Locale.ROOT);
    boolean unknown = !configured.equals("VANILLA") && !configured.equals("RESOURCE");
    if (unknown) {
      configured = DEFAULT_MODE;
    }
    boolean resourceMode = configured.equals("RESOURCE");
    WireCarrierMode carrierMode =
        wireCarrierMode == null ? WireCarrierMode.DEFAULT : wireCarrierMode;
    boolean configuredBarrierCarrier = carrierMode == WireCarrierMode.BARRIER;
    boolean resourceWireCarrierFallback =
        resourceMode && carrierMode == WireCarrierMode.CHORUS_PLANT && !chorusUpdatesDisabled;
    boolean resourceWireUsesBarrier =
        resourceMode && (configuredBarrierCarrier || resourceWireCarrierFallback);
    return new ModePolicy(
        configured, resourceMode, resourceWireUsesBarrier, resourceWireCarrierFallback, unknown);
  }
}
