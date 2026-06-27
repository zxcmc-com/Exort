package com.zxcmc.exort.runtime;

import com.zxcmc.exort.display.culling.DisplayCullingService;
import com.zxcmc.exort.display.device.BusDisplayManager;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.device.RelayDisplayManager;
import com.zxcmc.exort.display.device.StorageDisplayManager;
import com.zxcmc.exort.display.device.TerminalDisplayManager;
import com.zxcmc.exort.display.proxy.ExortBlockProxyService;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;

public record RuntimeDisplayServices(
    ItemHologramManager hologramManager,
    WireDisplayManager wireDisplayManager,
    StorageDisplayManager storageDisplayManager,
    TerminalDisplayManager terminalDisplayManager,
    MonitorDisplayManager monitorDisplayManager,
    BusDisplayManager busDisplayManager,
    RelayDisplayManager relayDisplayManager,
    ExortBlockProxyService blockProxyService,
    DisplayCullingService displayCullingService,
    DisplayRefreshService displayRefreshService) {}
