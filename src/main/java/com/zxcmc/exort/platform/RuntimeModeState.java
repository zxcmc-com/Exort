package com.zxcmc.exort.platform;

public record RuntimeModeState(
    String configuredMode,
    boolean resourceMode,
    boolean resourceWireUsesBarrier,
    boolean resourceWireCarrierFallback) {}
