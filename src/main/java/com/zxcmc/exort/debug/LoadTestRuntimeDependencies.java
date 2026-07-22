package com.zxcmc.exort.debug;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTierCatalog;
import java.util.Objects;
import org.bukkit.Material;

public record LoadTestRuntimeDependencies(
    StorageKeys keys,
    StorageManager storageManager,
    StorageTierCatalog storageTierCatalog,
    DisplayRefreshService displayRefreshService,
    BusService busService,
    NetworkGraphCache networkGraphCache,
    CarrierMaterials materials,
    ItemHologramManager hologramManager,
    MonitorDisplayManager monitorDisplayManager,
    int wireLimit,
    int wireHardCap,
    Material relayTraversalCarrier,
    int relayRangeChunks) {
  public LoadTestRuntimeDependencies {
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(storageTierCatalog, "storageTierCatalog");
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(materials, "materials");
  }
}
