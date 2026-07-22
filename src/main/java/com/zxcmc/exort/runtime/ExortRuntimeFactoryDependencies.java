package com.zxcmc.exort.runtime;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import com.zxcmc.exort.gui.SearchDialogService;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.WorldEditBridgeDependencies;
import com.zxcmc.exort.integration.worldedit.WorldEditIntegration;
import com.zxcmc.exort.items.InventoryRefreshService;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTierCatalog;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public record ExortRuntimeFactoryDependencies(
    CoreServices core,
    RuntimeConfigSnapshot runtimeConfig,
    RuntimeIntegrationContext integrations,
    RuntimeHooks hooks,
    PreparedRuntime preparedRuntime,
    RuntimeFaultController runtimeFaultController) {
  public ExortRuntimeFactoryDependencies {
    Objects.requireNonNull(core, "core");
    Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    Objects.requireNonNull(integrations, "integrations");
    Objects.requireNonNull(hooks, "hooks");
    Objects.requireNonNull(preparedRuntime, "preparedRuntime");
    Objects.requireNonNull(runtimeFaultController, "runtimeFaultController");
  }

  public JavaPlugin plugin() {
    return core.plugin();
  }

  public FileConfiguration config() {
    return runtimeConfig.config();
  }

  public Lang lang() {
    return core.lang();
  }

  public ItemNameService itemNameService() {
    return core.itemNameService();
  }

  public SearchDialogService searchDialogService() {
    return core.searchDialogService();
  }

  public StorageKeys keys() {
    return core.keys();
  }

  public StorageManager storageManager() {
    return core.storageManager();
  }

  public Database database() {
    return core.database();
  }

  public SessionManager sessionManager() {
    return core.sessionManager();
  }

  public BossBarManager bossBarManager() {
    return core.bossBarManager();
  }

  public PlayerFeedback playerFeedback() {
    return core.playerFeedback();
  }

  public InventoryRefreshService inventoryRefreshService() {
    return core.inventoryRefreshService();
  }

  public MaintenanceScheduler runtimeTasks() {
    return core.maintenanceScheduler();
  }

  public Supplier<NetworkGraphCache> networkGraphCache() {
    return integrations.networkGraphCache();
  }

  public Supplier<RegionProtection> regionProtection() {
    return integrations.regionProtection();
  }

  public Supplier<WorldEditDebugService> worldEditDebugService() {
    return integrations.worldEditDebugService();
  }

  public Supplier<BusService> busService() {
    return integrations.busService();
  }

  public RecipeService.Activation recipeActivation() {
    return preparedRuntime.recipeActivation();
  }

  public StorageTierCatalog storageTierCatalog() {
    return preparedRuntime.storageTierCatalog();
  }

  public boolean resourceMode() {
    return runtimeConfig.resourceMode();
  }

  public boolean resourceWireUsesBarrier() {
    return runtimeConfig.resourceWireUsesBarrier();
  }

  public Runnable reloadDefaultSortMode() {
    return hooks.reloadDefaultSortMode();
  }

  public Runnable setupRegionProtection() {
    return hooks.setupRegionProtection();
  }

  public Runnable revalidateSessions() {
    return hooks.revalidateSessions();
  }

  public Consumer<String> pickDebugSink() {
    return hooks.pickDebugSink();
  }

  public Consumer<String> pickDebugFullSink() {
    return hooks.pickDebugFullSink();
  }

  public Consumer<Block> monitorPlacedRecorder() {
    return hooks.monitorPlacedRecorder();
  }

  public Predicate<Block> monitorRecentlyPlaced() {
    return hooks.monitorRecentlyPlaced();
  }

  public Consumer<Block> transmitterPlacedRecorder() {
    return hooks.transmitterPlacedRecorder();
  }

  public Predicate<Block> transmitterRecentlyPlaced() {
    return hooks.transmitterRecentlyPlaced();
  }

  public Supplier<GuiRuntimeConfig> guiRuntimeConfig() {
    return runtimeConfig.guiRuntimeConfig();
  }

  public Supplier<GuiOverlayConfig> guiOverlayConfig() {
    return runtimeConfig.guiOverlayConfig();
  }

  public Consumer<String> renderStorage() {
    return hooks.renderStorage();
  }

  public Function<WorldEditBridgeDependencies, WorldEditIntegration> tryRegisterWorldEdit() {
    return integrations.tryRegisterWorldEdit();
  }

  public Consumer<WorldEditIntegration> worldEditIntegrationSink() {
    return integrations.worldEditIntegrationSink();
  }
}
