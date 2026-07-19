package com.zxcmc.exort.block.listener;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.placement.bridge.PlacementRefreshContext;
import com.zxcmc.exort.placement.bridge.PlacementRuntimeContext;
import com.zxcmc.exort.placement.bridge.PlacementServices;
import com.zxcmc.exort.placement.storage.StoragePlacementService;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

public record CarrierListenerDependencies(
    PlacementServices services,
    CarrierMaterials materials,
    PlacementRuntimeContext runtime,
    PlacementRefreshContext refresh,
    WireDisplayManager wireDisplayManager,
    BlockBreakHandler breakHandler) {
  public CarrierListenerDependencies {
    Objects.requireNonNull(services, "services");
    Objects.requireNonNull(materials, "materials");
    Objects.requireNonNull(runtime, "runtime");
    Objects.requireNonNull(refresh, "refresh");
    Objects.requireNonNull(breakHandler, "breakHandler");
  }

  public JavaPlugin plugin() {
    return services.plugin();
  }

  public CustomItems customItems() {
    return services.customItems();
  }

  public StorageKeys keys() {
    return services.keys();
  }

  public StoragePlacementService storagePlacementService() {
    return services.storagePlacementService();
  }

  public Material wireMaterial() {
    return materials.wire();
  }

  public int wireHardCap() {
    return runtime.wireHardCap();
  }

  public ItemHologramManager hologramManager() {
    return refresh.hologramManager().get();
  }

  public Supplier<ItemHologramManager> hologramManagerSource() {
    return refresh.hologramManager();
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

  public boolean relayEnabled() {
    return runtime.relayEnabled();
  }

  public Material transmitterCarrier() {
    return materials.transmitterCarrier();
  }

  public boolean wirelessEnabled() {
    return runtime.wirelessEnabled();
  }

  public WirelessTransmitterService wirelessTransmitterService() {
    return services.wirelessTransmitterService();
  }

  public Material chunkLoaderCarrier() {
    return materials.chunkLoaderCarrier();
  }

  public ChunkLoaderService chunkLoaderService() {
    return services.chunkLoaderService();
  }

  public RegionProtection regionProtection() {
    return services.regionProtection();
  }

  public PlayerFeedback playerFeedback() {
    return services.playerFeedback();
  }

  public Supplier<DisplayRefreshService> displayRefreshService() {
    return refresh.displayRefreshService();
  }

  public Supplier<MonitorDisplayManager> monitorDisplayManager() {
    return refresh.monitorDisplayManager();
  }

  public Supplier<BusService> busService() {
    return refresh.busService();
  }

  public Supplier<NetworkGraphCache> networkGraphCache() {
    return refresh.networkGraphCache();
  }

  public Runnable revalidateSessions() {
    return refresh.revalidateSessions();
  }

  public Consumer<Block> transmitterPlacedRecorder() {
    return refresh.transmitterPlacedRecorder();
  }

  public Supplier<BreakSoundConfig> breakSoundConfig() {
    return runtime.breakSoundConfig();
  }

  public Supplier<BusRuntimeConfig> busRuntimeConfig() {
    return runtime.busRuntimeConfig();
  }
}
