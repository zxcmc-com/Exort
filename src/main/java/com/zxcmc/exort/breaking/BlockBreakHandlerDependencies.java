package com.zxcmc.exort.breaking;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public record BlockBreakHandlerDependencies(
    JavaPlugin plugin,
    CustomItems customItems,
    Material wireMaterial,
    Material storageCarrier,
    Material terminalCarrier,
    Material monitorCarrier,
    Material busCarrier,
    Material relayCarrier,
    Material transmitterCarrier,
    WirelessTransmitterService wirelessTransmitterService,
    TransmitterSessionManager transmitterSessionManager,
    Material chunkLoaderCarrier,
    ItemHologramManager hologramManager,
    WireDisplayManager wireDisplayManager,
    DisplayRefreshService displayRefreshService,
    BreakAnimationSender breakAnimationSender,
    StorageManager storageManager,
    SessionManager sessionManager,
    Supplier<MonitorDisplayManager> monitorDisplayManager,
    Supplier<BusSessionManager> busSessionManager,
    Supplier<BusService> busService,
    Supplier<NetworkGraphCache> networkGraphCache,
    RegionProtection regionProtection,
    PlayerFeedback playerFeedback,
    ChunkLoaderService chunkLoaderService) {
  public BlockBreakHandlerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(customItems, "customItems");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(terminalCarrier, "terminalCarrier");
    Objects.requireNonNull(monitorCarrier, "monitorCarrier");
    Objects.requireNonNull(busCarrier, "busCarrier");
    Objects.requireNonNull(relayCarrier, "relayCarrier");
    Objects.requireNonNull(transmitterCarrier, "transmitterCarrier");
    Objects.requireNonNull(wirelessTransmitterService, "wirelessTransmitterService");
    Objects.requireNonNull(transmitterSessionManager, "transmitterSessionManager");
    Objects.requireNonNull(chunkLoaderCarrier, "chunkLoaderCarrier");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(sessionManager, "sessionManager");
    Objects.requireNonNull(monitorDisplayManager, "monitorDisplayManager");
    Objects.requireNonNull(busSessionManager, "busSessionManager");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(chunkLoaderService, "chunkLoaderService");
  }
}
