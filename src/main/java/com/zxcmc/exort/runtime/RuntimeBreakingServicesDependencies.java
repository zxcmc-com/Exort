package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BreakAnimationSender;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record RuntimeBreakingServicesDependencies(
    JavaPlugin plugin,
    FileConfiguration config,
    Logger logger,
    CustomItems customItems,
    RuntimeMaterials materials,
    ItemHologramManager hologramManager,
    WireDisplayManager wireDisplayManager,
    DisplayRefreshService displayRefreshService,
    StorageManager storageManager,
    SessionManager sessionManager,
    Supplier<MonitorDisplayManager> monitorDisplayManager,
    Supplier<BusSessionManager> busSessionManager,
    Supplier<BusService> busService,
    Supplier<NetworkGraphCache> networkGraphCache,
    RegionProtection regionProtection,
    WorldEditWandGuard worldEditWandGuard,
    PlayerFeedback playerFeedback,
    BreakAnimationSender breakAnimationSender,
    PacketEnhancements packetEnhancements) {
  public RuntimeBreakingServicesDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(logger, "logger");
    Objects.requireNonNull(customItems, "customItems");
    Objects.requireNonNull(materials, "materials");
    Objects.requireNonNull(hologramManager, "hologramManager");
    Objects.requireNonNull(wireDisplayManager, "wireDisplayManager");
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(sessionManager, "sessionManager");
    Objects.requireNonNull(monitorDisplayManager, "monitorDisplayManager");
    Objects.requireNonNull(busSessionManager, "busSessionManager");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(worldEditWandGuard, "worldEditWandGuard");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(breakAnimationSender, "breakAnimationSender");
  }
}
