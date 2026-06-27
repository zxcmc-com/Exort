package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.breaking.CustomBlockBreaker;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.display.device.BusDisplayManager;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.device.TerminalDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.SearchDialogService;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.items.InventoryRefreshService;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.recipes.CraftingRulesConfig;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

public record RuntimeListenerDependencies(
    JavaPlugin plugin,
    Database database,
    StorageManager storageManager,
    SessionManager sessionManager,
    StorageKeys keys,
    CustomItems customItems,
    WirelessTerminalService wirelessService,
    RegionProtection regionProtection,
    AuthenticationGate authenticationGate,
    WorldEditWandGuard worldEditWandGuard,
    PlayerFeedback playerFeedback,
    BossBarManager bossBarManager,
    SearchDialogService searchDialogService,
    Lang lang,
    ItemNameService itemNameService,
    InventoryRefreshService inventoryRefreshService,
    RuntimeMaterials materials,
    int wireLimit,
    int wireHardCap,
    int relayRangeChunks,
    ItemHologramManager hologramManager,
    Supplier<ItemHologramManager> hologramManagerSource,
    WireDisplayManager wireDisplayManager,
    TerminalDisplayManager terminalDisplayManager,
    MonitorDisplayManager monitorDisplayManager,
    BusDisplayManager busDisplayManager,
    DisplayRefreshService displayRefreshService,
    BusService busService,
    BusSessionManager busSessionManager,
    CustomBlockBreaker customBlockBreaker,
    BlockBreakHandler breakHandler,
    BreakSoundConfig breakSoundConfig,
    PacketEnhancements packetEnhancements,
    RecipeService previousRecipeService,
    BusRuntimeConfig busRuntimeConfig,
    CraftingRulesConfig craftingConfig,
    long storagePeekTicks,
    long wirePeekTicks,
    Runnable revalidateSessions,
    Consumer<String> pickDebugSink,
    Consumer<Block> monitorPlacedRecorder,
    Predicate<Block> monitorRecentlyPlaced) {
  public RuntimeListenerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(sessionManager, "sessionManager");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(customItems, "customItems");
    Objects.requireNonNull(wirelessService, "wirelessService");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(authenticationGate, "authenticationGate");
    Objects.requireNonNull(worldEditWandGuard, "worldEditWandGuard");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(searchDialogService, "searchDialogService");
    Objects.requireNonNull(lang, "lang");
    Objects.requireNonNull(itemNameService, "itemNameService");
    Objects.requireNonNull(inventoryRefreshService, "inventoryRefreshService");
    Objects.requireNonNull(materials, "materials");
    Objects.requireNonNull(hologramManagerSource, "hologramManagerSource");
    Objects.requireNonNull(wireDisplayManager, "wireDisplayManager");
    Objects.requireNonNull(terminalDisplayManager, "terminalDisplayManager");
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(busSessionManager, "busSessionManager");
    Objects.requireNonNull(customBlockBreaker, "customBlockBreaker");
    Objects.requireNonNull(breakHandler, "breakHandler");
    Objects.requireNonNull(breakSoundConfig, "breakSoundConfig");
    Objects.requireNonNull(busRuntimeConfig, "busRuntimeConfig");
    Objects.requireNonNull(craftingConfig, "craftingConfig");
    Objects.requireNonNull(revalidateSessions, "revalidateSessions");
    Objects.requireNonNull(pickDebugSink, "pickDebugSink");
    Objects.requireNonNull(monitorPlacedRecorder, "monitorPlacedRecorder");
    Objects.requireNonNull(monitorRecentlyPlaced, "monitorRecentlyPlaced");
  }

  public Supplier<DisplayRefreshService> displayRefreshServiceSource() {
    return () -> displayRefreshService;
  }

  public Supplier<MonitorDisplayManager> monitorDisplayManagerSource() {
    return () -> monitorDisplayManager;
  }

  public Supplier<BusService> busServiceSource() {
    return () -> busService;
  }

  public Supplier<NetworkGraphCache> networkGraphCacheSource() {
    return plugin instanceof com.zxcmc.exort.network.NetworkGraphCacheProvider provider
        ? provider::getNetworkGraphCache
        : () -> null;
  }
}
