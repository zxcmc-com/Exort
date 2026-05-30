package com.zxcmc.exort.runtime;

import com.zxcmc.exort.display.BusDisplayManager;
import com.zxcmc.exort.display.DisplayCullingService;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.ExortBlockProxyService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.MonitorDisplayManager;
import com.zxcmc.exort.display.StorageDisplayManager;
import com.zxcmc.exort.display.TerminalDisplayManager;
import com.zxcmc.exort.display.WireDisplayManager;

public record RuntimeDisplayServices(
    ItemHologramManager hologramManager,
    WireDisplayManager wireDisplayManager,
    StorageDisplayManager storageDisplayManager,
    TerminalDisplayManager terminalDisplayManager,
    MonitorDisplayManager monitorDisplayManager,
    BusDisplayManager busDisplayManager,
    ExortBlockProxyService blockProxyService,
    DisplayCullingService displayCullingService,
    DisplayRefreshService displayRefreshService) {}
