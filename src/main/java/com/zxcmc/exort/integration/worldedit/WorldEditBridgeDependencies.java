package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

public record WorldEditBridgeDependencies(
    Plugin plugin,
    Database database,
    StorageManager storageManager,
    Supplier<WorldEditDebugService> debugServiceSource,
    Supplier<NetworkGraphCache> networkGraphCacheSource,
    Supplier<DisplayRefreshService> displayRefreshServiceSource,
    Supplier<BusService> busServiceSource,
    Supplier<ItemHologramManager> hologramManagerSource,
    WorldEditBridgeMaterials materials,
    WorldEditBulkConfig bulkConfig) {
  public WorldEditBridgeDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(debugServiceSource, "debugServiceSource");
    Objects.requireNonNull(networkGraphCacheSource, "networkGraphCacheSource");
    Objects.requireNonNull(displayRefreshServiceSource, "displayRefreshServiceSource");
    Objects.requireNonNull(busServiceSource, "busServiceSource");
    Objects.requireNonNull(hologramManagerSource, "hologramManagerSource");
    Objects.requireNonNull(materials, "materials");
    Objects.requireNonNull(bulkConfig, "bulkConfig");
  }

  public WorldEditDebugService debugService() {
    return debugServiceSource.get();
  }

  public NetworkGraphCache networkGraphCache() {
    return networkGraphCacheSource.get();
  }

  public DisplayRefreshService displayRefreshService() {
    return displayRefreshServiceSource.get();
  }

  public BusService busService() {
    return busServiceSource.get();
  }

  public ItemHologramManager hologramManager() {
    return hologramManagerSource.get();
  }

  public Material wireMaterial() {
    return materials.wire();
  }

  public Material storageCarrier() {
    return materials.storageCarrier();
  }

  public Material terminalCarrier() {
    return materials.terminalCarrier();
  }

  public Material monitorCarrier() {
    return materials.monitorCarrier();
  }

  public Material busCarrier() {
    return materials.busCarrier();
  }

  public Material relayCarrier() {
    return materials.relayCarrier();
  }
}
