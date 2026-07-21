package com.zxcmc.exort.runtime;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.relay.RelaySetupTracker;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public record RuntimeDisplayServicesDependencies(
    JavaPlugin plugin,
    ConfigurationSection config,
    Lang lang,
    StorageKeys keys,
    WirelessRuntimeConfig wirelessConfig,
    StorageTierCatalog storageTierCatalog,
    StorageManager storageManager,
    Database database,
    CarrierMaterials materials,
    RuntimeItemModelConfig itemModels,
    RuntimeHologramConfig hologramConfig,
    boolean resourceMode,
    RelaySetupTracker relaySetupTracker,
    int wireLimit,
    int wireHardCap,
    Material relayTraversalCarrier,
    int relayRangeChunks,
    PacketEnhancements packetEnhancements,
    Supplier<WorldEditDebugService> worldEditDebugService,
    Supplier<BusService> busService,
    Supplier<WirelessTransmitterService> wirelessTransmitterService,
    Supplier<TransmitterSessionManager> transmitterSessionManager,
    Supplier<NetworkGraphCache> networkGraphCache,
    Consumer<Chunk> invalidateNetworkChunk) {
  public RuntimeDisplayServicesDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(lang, "lang");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(wirelessConfig, "wirelessConfig");
    Objects.requireNonNull(storageTierCatalog, "storageTierCatalog");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(materials, "materials");
    Objects.requireNonNull(itemModels, "itemModels");
    Objects.requireNonNull(hologramConfig, "hologramConfig");
    Objects.requireNonNull(relaySetupTracker, "relaySetupTracker");
    Objects.requireNonNull(worldEditDebugService, "worldEditDebugService");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(wirelessTransmitterService, "wirelessTransmitterService");
    Objects.requireNonNull(transmitterSessionManager, "transmitterSessionManager");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(invalidateNetworkChunk, "invalidateNetworkChunk");
  }
}
