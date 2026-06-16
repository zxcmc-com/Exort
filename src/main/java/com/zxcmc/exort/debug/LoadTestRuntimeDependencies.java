package com.zxcmc.exort.debug;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.MonitorDisplayManager;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.runtime.RuntimeMaterials;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;

public record LoadTestRuntimeDependencies(
    StorageKeys keys,
    StorageManager storageManager,
    DisplayRefreshService displayRefreshService,
    BusService busService,
    NetworkGraphCache networkGraphCache,
    RuntimeMaterials materials,
    ItemHologramManager hologramManager,
    MonitorDisplayManager monitorDisplayManager,
    int wireLimit,
    int wireHardCap,
    int relayRangeChunks) {
  public LoadTestRuntimeDependencies {
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(materials, "materials");
  }
}
