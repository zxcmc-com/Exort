package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.breaking.CustomBlockBreaker;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.display.culling.DisplayCullingService;
import com.zxcmc.exort.display.device.BusDisplayManager;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.device.StorageDisplayManager;
import com.zxcmc.exort.display.device.TerminalDisplayManager;
import com.zxcmc.exort.display.proxy.ExortBlockProxyService;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.integration.worldedit.WorldEditIntegration;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.placement.RightClickPlacementGuard;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.relay.RelaySetupTracker;
import com.zxcmc.exort.storage.StorageClaimRegistry;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import org.bukkit.Material;

public record ExortRuntimeServices(
    RuntimeItemNamesReload itemNamesReload,
    CustomItems customItems,
    WirelessTerminalService wirelessService,
    WirelessTransmitterService wirelessTransmitterService,
    TransmitterSessionManager transmitterSessionManager,
    ChunkLoaderService chunkLoaderService,
    StorageClaimRegistry storageClaimRegistry,
    RelaySetupTracker relaySetupTracker,
    CarrierMaterials materials,
    Material relayTraversalCarrier,
    int wireLimit,
    int wireHardCap,
    int relayRangeChunks,
    long storagePeekTicks,
    long wirePeekTicks,
    ItemHologramManager hologramManager,
    WireDisplayManager wireDisplayManager,
    StorageDisplayManager storageDisplayManager,
    TerminalDisplayManager terminalDisplayManager,
    MonitorDisplayManager monitorDisplayManager,
    BusDisplayManager busDisplayManager,
    ExortBlockProxyService blockProxyService,
    DisplayCullingService displayCullingService,
    DisplayRefreshService displayRefreshService,
    BusService busService,
    BusSessionManager busSessionManager,
    BlockBreakHandler breakHandler,
    CustomBlockBreaker customBlockBreaker,
    BreakSoundConfig breakSoundConfig,
    CraftingRules craftingRules,
    RecipeService recipeService,
    PacketEnhancements packetEnhancements,
    RightClickPlacementGuard placementGuard,
    WorldEditIntegration worldEditIntegration) {
  public java.util.concurrent.CompletableFuture<ItemNameService.Status> itemNamesStatus() {
    return itemNamesReload.start();
  }
}
