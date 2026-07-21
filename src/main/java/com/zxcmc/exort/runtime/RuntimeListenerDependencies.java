package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.breaking.CustomBlockBreaker;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.display.device.BusDisplayManager;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.device.TerminalDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.recipes.CraftingRulesConfig;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.relay.RelaySetupTracker;
import com.zxcmc.exort.storage.StorageClaimRegistry;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Cohesive inputs for one generation of Bukkit listener registration. */
public record RuntimeListenerDependencies(
    CoreServices core,
    RuntimeConfigSnapshot runtimeConfig,
    CarrierMaterials materials,
    RuntimeNetworkConfig network,
    RuntimeListenerDomainServices domain,
    RuntimeDisplayServices display,
    RuntimeBusServices bus,
    RuntimeBreakingServices breaking,
    RuntimeListenerIntegrations integrations,
    RuntimeListenerPolicies policies,
    RuntimeHooks hooks,
    RuntimeGenerationScope generationScope,
    StorageTierCatalog storageTierCatalog,
    RuntimeHandle.Scope resources) {
  public RuntimeListenerDependencies {
    Objects.requireNonNull(core, "core");
    Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    Objects.requireNonNull(materials, "materials");
    Objects.requireNonNull(network, "network");
    Objects.requireNonNull(domain, "domain");
    Objects.requireNonNull(display, "display");
    Objects.requireNonNull(bus, "bus");
    Objects.requireNonNull(breaking, "breaking");
    Objects.requireNonNull(integrations, "integrations");
    Objects.requireNonNull(policies, "policies");
    Objects.requireNonNull(hooks, "hooks");
    Objects.requireNonNull(generationScope, "generationScope");
    Objects.requireNonNull(storageTierCatalog, "storageTierCatalog");
    Objects.requireNonNull(resources, "resources");
  }

  public ItemHologramManager hologramManager() {
    return display.hologramManager();
  }

  public Supplier<ItemHologramManager> hologramManagerSource() {
    return display::hologramManager;
  }

  public Supplier<DisplayRefreshService> displayRefreshServiceSource() {
    return display::displayRefreshService;
  }

  public Supplier<MonitorDisplayManager> monitorDisplayManagerSource() {
    return display::monitorDisplayManager;
  }

  public Supplier<BusService> busServiceSource() {
    return bus::busService;
  }

  public CustomItems customItems() {
    return domain.customItems();
  }

  public StorageClaimRegistry storageClaimRegistry() {
    return domain.storageClaimRegistry();
  }

  public WirelessTerminalService wirelessService() {
    return domain.wirelessService();
  }

  public WirelessTransmitterService wirelessTransmitterService() {
    return domain.wirelessTransmitterService();
  }

  public TransmitterSessionManager transmitterSessionManager() {
    return domain.transmitterSessionManager();
  }

  public ChunkLoaderService chunkLoaderService() {
    return domain.chunkLoaderService();
  }

  public RelaySetupTracker relaySetupTracker() {
    return domain.relaySetupTracker();
  }

  public RegionProtection regionProtection() {
    return integrations.regionProtection();
  }

  public AuthenticationGate authenticationGate() {
    return integrations.authenticationGate();
  }

  public WorldEditWandGuard worldEditWandGuard() {
    return integrations.worldEditWandGuard();
  }

  public PacketEnhancements packetEnhancements() {
    return integrations.packetEnhancements();
  }

  public RecipeService.Activation recipeActivation() {
    return integrations.recipeActivation();
  }

  public BusRuntimeConfig busRuntimeConfig() {
    return policies.busRuntimeConfig();
  }

  public CraftingRulesConfig craftingConfig() {
    return policies.craftingConfig();
  }

  public WireDisplayManager wireDisplayManager() {
    return display.wireDisplayManager();
  }

  public TerminalDisplayManager terminalDisplayManager() {
    return display.terminalDisplayManager();
  }

  public MonitorDisplayManager monitorDisplayManager() {
    return display.monitorDisplayManager();
  }

  public BusDisplayManager busDisplayManager() {
    return display.busDisplayManager();
  }

  public DisplayRefreshService displayRefreshService() {
    return display.displayRefreshService();
  }

  public BusService busService() {
    return bus.busService();
  }

  public BusSessionManager busSessionManager() {
    return bus.busSessionManager();
  }

  public CustomBlockBreaker customBlockBreaker() {
    return breaking.customBlockBreaker();
  }

  public BlockBreakHandler breakHandler() {
    return breaking.breakHandler();
  }

  public BreakSoundConfig breakSoundConfig() {
    return breaking.breakSoundConfig();
  }

  public Supplier<NetworkGraphCache> networkGraphCacheSource() {
    return integrations.networkGraphCache();
  }

  public JavaPlugin plugin() {
    return core.plugin();
  }

  public FileConfiguration config() {
    return runtimeConfig.config();
  }

  public com.zxcmc.exort.infra.db.Database database() {
    return core.database();
  }

  public com.zxcmc.exort.storage.StorageManager storageManager() {
    return core.storageManager();
  }

  public com.zxcmc.exort.gui.SessionManager sessionManager() {
    return core.sessionManager();
  }

  public com.zxcmc.exort.keys.StorageKeys keys() {
    return core.keys();
  }

  public com.zxcmc.exort.feedback.PlayerFeedback playerFeedback() {
    return core.playerFeedback();
  }

  public com.zxcmc.exort.feedback.BossBarManager bossBarManager() {
    return core.bossBarManager();
  }

  public com.zxcmc.exort.gui.SearchDialogService searchDialogService() {
    return core.searchDialogService();
  }

  public com.zxcmc.exort.i18n.Lang lang() {
    return core.lang();
  }

  public com.zxcmc.exort.i18n.ItemNameService itemNameService() {
    return core.itemNameService();
  }

  public com.zxcmc.exort.items.InventoryRefreshService inventoryRefreshService() {
    return core.inventoryRefreshService();
  }

  public int wireLimit() {
    return network.wireLimit();
  }

  public int wireHardCap() {
    return network.wireHardCap();
  }

  public boolean relayEnabled() {
    return network.relayEnabled();
  }

  public int relayRangeChunks() {
    return network.relayRangeChunks();
  }

  public long storagePeekTicks() {
    return network.storagePeekTicks();
  }

  public long wirePeekTicks() {
    return network.wirePeekTicks();
  }

  public Material relayTraversalCarrier() {
    return policies.relayTraversalCarrier();
  }

  public Runnable revalidateSessions() {
    return hooks.revalidateSessions();
  }

  public java.util.function.Consumer<String> pickDebugSink() {
    return hooks.pickDebugSink();
  }

  public java.util.function.Consumer<Block> monitorPlacedRecorder() {
    return hooks.monitorPlacedRecorder();
  }

  public java.util.function.Predicate<Block> monitorRecentlyPlaced() {
    return hooks.monitorRecentlyPlaced();
  }

  public java.util.function.Consumer<Block> transmitterPlacedRecorder() {
    return hooks.transmitterPlacedRecorder();
  }

  public java.util.function.Predicate<Block> transmitterRecentlyPlaced() {
    return hooks.transmitterRecentlyPlaced();
  }
}
