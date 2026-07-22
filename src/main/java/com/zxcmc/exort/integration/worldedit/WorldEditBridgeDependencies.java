package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.runtime.RuntimeGenerationScope;
import com.zxcmc.exort.storage.StorageClaimRegistry;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

public record WorldEditBridgeDependencies(
    Plugin plugin,
    Database database,
    StorageManager storageManager,
    StorageClaimRegistry storageClaimRegistry,
    Supplier<WorldEditDebugService> debugServiceSource,
    Supplier<NetworkGraphCache> networkGraphCacheSource,
    Supplier<DisplayRefreshService> displayRefreshServiceSource,
    Supplier<BusService> busServiceSource,
    Supplier<ChunkLoaderService> chunkLoaderServiceSource,
    Supplier<WirelessTransmitterService> wirelessTransmitterServiceSource,
    Supplier<TransmitterSessionManager> transmitterSessionManagerSource,
    Supplier<ItemHologramManager> hologramManagerSource,
    StorageTierCatalog storageTierCatalog,
    CarrierMaterials materials,
    WorldEditBulkConfig bulkConfig,
    boolean autoConfigureFawe,
    RuntimeGenerationScope generationScope) {
  public WorldEditBridgeDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(storageClaimRegistry, "storageClaimRegistry");
    Objects.requireNonNull(debugServiceSource, "debugServiceSource");
    Objects.requireNonNull(networkGraphCacheSource, "networkGraphCacheSource");
    Objects.requireNonNull(displayRefreshServiceSource, "displayRefreshServiceSource");
    Objects.requireNonNull(busServiceSource, "busServiceSource");
    Objects.requireNonNull(chunkLoaderServiceSource, "chunkLoaderServiceSource");
    Objects.requireNonNull(wirelessTransmitterServiceSource, "wirelessTransmitterServiceSource");
    Objects.requireNonNull(transmitterSessionManagerSource, "transmitterSessionManagerSource");
    Objects.requireNonNull(hologramManagerSource, "hologramManagerSource");
    Objects.requireNonNull(storageTierCatalog, "storageTierCatalog");
    Objects.requireNonNull(materials, "materials");
    Objects.requireNonNull(bulkConfig, "bulkConfig");
    Objects.requireNonNull(generationScope, "generationScope");
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

  public ChunkLoaderService chunkLoaderService() {
    return chunkLoaderServiceSource.get();
  }

  public WirelessTransmitterService wirelessTransmitterService() {
    return wirelessTransmitterServiceSource.get();
  }

  public TransmitterSessionManager transmitterSessionManager() {
    return transmitterSessionManagerSource.get();
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

  public Material transmitterCarrier() {
    return materials.transmitterCarrier();
  }

  public Material chunkLoaderCarrier() {
    return materials.chunkLoaderCarrier();
  }
}
