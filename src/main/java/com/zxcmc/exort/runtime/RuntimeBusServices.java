package com.zxcmc.exort.runtime;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;

public record RuntimeBusServices(BusService busService, BusSessionManager busSessionManager) {}
