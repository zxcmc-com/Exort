package com.zxcmc.exort.core;

import com.zxcmc.exort.api.ExortApi;
import com.zxcmc.exort.api.model.StorageTierDescriptor;
import com.zxcmc.exort.block.ExortBlockClassifier;
import com.zxcmc.exort.breaking.overlay.DisplayBreakAnimationSender;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.carrier.WireCarrierMode;
import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.command.CommandRuntimeAccess;
import com.zxcmc.exort.command.ExortBrigadier;
import com.zxcmc.exort.command.ExortBrigadierDependencies;
import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.debug.LoadTestRuntimeDependencies;
import com.zxcmc.exort.debug.LoadTestService;
import com.zxcmc.exort.debug.PickDebugService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.culling.DisplayCullingService;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.proxy.ExortBlockProxyService;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import com.zxcmc.exort.gui.SearchDialogService;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.gui.SessionManagerDependencies;
import com.zxcmc.exort.gui.SortEvent;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.i18n.PlayerLocaleService;
import com.zxcmc.exort.infra.config.ConfigUpdater;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.metrics.ExortMetrics;
import com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackService;
import com.zxcmc.exort.infra.update.UpdateChecker;
import com.zxcmc.exort.integration.chorusfix.ChorusfixIntegration;
import com.zxcmc.exort.integration.chorusfix.embedded.EmbeddedChorusfixConfig;
import com.zxcmc.exort.integration.chorusfix.embedded.EmbeddedChorusfixController;
import com.zxcmc.exort.integration.chorusfix.embedded.EmbeddedChorusfixStatus;
import com.zxcmc.exort.integration.protection.CompositeRegionProtection;
import com.zxcmc.exort.integration.protection.MutableRegionProtection;
import com.zxcmc.exort.integration.protection.ProtectionRuntimeConfig;
import com.zxcmc.exort.integration.protection.ProtectionStatus;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.resourcepack.nexo.NexoResourcePackIntegration;
import com.zxcmc.exort.integration.resourcepack.oraxen.OraxenResourcePackIntegration;
import com.zxcmc.exort.integration.worldedit.WorldEditIntegration;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.items.InventoryRefreshService;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.placement.RecentPlacementTracker;
import com.zxcmc.exort.platform.MinecraftVersionRequirement;
import com.zxcmc.exort.platform.ModePolicy;
import com.zxcmc.exort.platform.PaperChorusPlantUpdates;
import com.zxcmc.exort.platform.RuntimeModeCoordinator;
import com.zxcmc.exort.platform.RuntimeModeState;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.runtime.CoreServices;
import com.zxcmc.exort.runtime.ExortRuntimeFactory;
import com.zxcmc.exort.runtime.ExortRuntimeFactoryDependencies;
import com.zxcmc.exort.runtime.ExortRuntimeServices;
import com.zxcmc.exort.runtime.MaintenanceScheduler;
import com.zxcmc.exort.runtime.RuntimeConfigSnapshot;
import com.zxcmc.exort.runtime.RuntimeHandle;
import com.zxcmc.exort.runtime.RuntimeHooks;
import com.zxcmc.exort.runtime.RuntimeIntegrationContext;
import com.zxcmc.exort.runtime.RuntimeReloadCoordinator;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageRuntimeConfig;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.sort.SortMode;
import com.zxcmc.exort.text.ExortText;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ExortPlugin extends JavaPlugin implements ExortApi {
  static final String MODE_FIX_RESOURCE_COMMAND = "/exort mode fix RESOURCE";
  private static final MinecraftVersionRequirement MIN_MC_VERSION =
      MinecraftVersionRequirement.atLeast(1, 21, 11);
  private Database database;
  private StorageManager storageManager;
  private SessionManager sessionManager;
  private StorageKeys keys;
  private Lang lang;
  private PlayerFeedback playerFeedback;
  private ItemNameService itemNameService;
  private PlayerLocaleService playerLocaleService;
  private CustomItems customItems;
  private WirelessTerminalService wirelessService;
  private WirelessTransmitterService wirelessTransmitterService;
  private TransmitterSessionManager transmitterSessionManager;
  private ChunkLoaderService chunkLoaderService;
  private BossBarManager bossBarManager;
  private SearchDialogService searchDialogService;
  private InventoryRefreshService inventoryRefreshService;
  private RecentPlacementTracker placementTracker;
  private MaintenanceScheduler runtimeTasks;
  private int wireLimit;
  private int wireHardCap;
  private int relayRangeChunks;
  private Material wireMaterial;
  private Material storageCarrier;
  private Material relayTraversalCarrier;
  private Material terminalCarrier;
  private CarrierMaterials runtimeMaterials;
  private ExortBlockClassifier blockClassifier;
  private ItemHologramManager hologramManager;
  private MonitorDisplayManager monitorDisplayManager;
  private ExortBlockProxyService blockProxyService;
  private DisplayCullingService displayCullingService;
  private BusService busService;
  private BusSessionManager busSessionManager;
  private CraftingRules craftingRules;
  private RecipeService recipeService;
  private LoadTestService loadTestService;
  private CacheDebugService cacheDebugService;
  private PickDebugService pickDebugService;
  private WorldEditDebugService worldEditDebugService;
  private Metrics metrics;
  private ResourcePackService resourcePackService;
  private NexoResourcePackIntegration nexoResourcePackIntegration;
  private OraxenResourcePackIntegration oraxenResourcePackIntegration;
  private EmbeddedChorusfixController embeddedChorusfix;
  private NetworkGraphCache networkGraphCache;
  private RuntimeHandle<ExortRuntimeServices> runtimeHandle;
  private FileConfiguration activeRuntimeConfig;
  private boolean resourceMode;
  private boolean resourceWireUsesBarrier;
  private boolean resourceWireCarrierFallback;
  private boolean chorusfixIntegrationLogged;
  private String configuredMode = "RESOURCE";
  private volatile String defaultSortModeName = SortMode.AMOUNT.name();
  private final MutableRegionProtection regionProtection =
      new MutableRegionProtection(RegionProtection.allowAll());
  private volatile ProtectionStatus protectionStatus =
      ProtectionStatus.noProvider(false, List.of());

  @Override
  public void onEnable() {
    if (!ensureMinMinecraftVersion()) {
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    if (!prepareConfigFiles()) {
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    activeRuntimeConfig = getConfig();
    new UpdateChecker(this).checkAsync();
    applyModePolicy(evaluateModePolicy(activeRuntimeConfig));
    if (!initCoreServices()) {
      return;
    }
    registerRuntime(true);
    reloadResourcePackService();
    registerBrigadierCommands();
    scheduleEmbeddedChorusfixFinalLog();
  }

  private boolean prepareConfigFiles() {
    saveDefaultConfig();
    if (!ConfigUpdater.update(this, "config.yml")) {
      return false;
    }
    reloadConfig();
    if (!ensureStorageTiersFile()) {
      return false;
    }
    ensureRecipesFile();
    return true;
  }

  private boolean initCoreServices() {
    lang = new Lang(this);
    itemNameService = new ItemNameService(this);
    nexoResourcePackIntegration = new NexoResourcePackIntegration();
    oraxenResourcePackIntegration = new OraxenResourcePackIntegration();
    resourcePackService =
        new ResourcePackService(
            this,
            () -> resourceMode,
            lang::tr,
            ExortText::configRichText,
            nexoResourcePackIntegration);
    Bukkit.getPluginManager().registerEvents(resourcePackService, this);
    playerFeedback = new PlayerFeedback(lang);
    playerLocaleService =
        new PlayerLocaleService(
            this,
            lang,
            itemNameService,
            () -> sessionManager,
            () -> busSessionManager,
            () -> transmitterSessionManager);
    Bukkit.getPluginManager().registerEvents(playerLocaleService, this);
    searchDialogService = new SearchDialogService(lang);
    keys = new StorageKeys(this);
    networkGraphCache = new NetworkGraphCache(this);
    database = new Database(getLogger(), () -> defaultSortModeName);
    File dbFile = new File(new File(getDataFolder(), "db"), "storage.db");
    try {
      database.init(dbFile);
    } catch (SQLException e) {
      getLogger().log(Level.SEVERE, "Failed to init database", e);
      getServer().getPluginManager().disablePlugin(this);
      return false;
    }
    storageManager =
        new StorageManager(
            database,
            this,
            this::isEnabled,
            keys,
            getLogger(),
            () -> cacheDebugService,
            () -> defaultSortModeName,
            database::setStorageSortMode,
            cache -> cache.refreshCustomItems(customItems, wirelessService, true));
    runtimeTasks =
        new MaintenanceScheduler(
            this, () -> storageManager, () -> StorageRuntimeConfig.fromConfig(getConfig()));
    inventoryRefreshService = new InventoryRefreshService(() -> customItems, () -> wirelessService);
    placementTracker = new RecentPlacementTracker();
    sessionManager =
        new SessionManager(
            new SessionManagerDependencies(
                this,
                database,
                storageManager,
                keys,
                lang,
                itemNameService,
                searchDialogService,
                () -> bossBarManager,
                () -> playerFeedback,
                () -> wirelessService,
                () -> wirelessTransmitterService,
                () -> busService,
                () -> craftingRules,
                () -> resourceMode,
                () -> regionProtection,
                () -> wireLimit,
                () -> wireHardCap,
                () -> relayRangeChunks,
                () -> wireMaterial,
                () -> storageCarrier,
                () -> relayTraversalCarrier,
                () -> terminalCarrier,
                () -> networkGraphCache,
                () -> GuiRuntimeConfig.fromConfig(getConfig()),
                GuiOverlayConfig::defaults,
                storageId -> {
                  if (monitorDisplayManager != null) {
                    monitorDisplayManager.refreshStorageMonitors(storageId);
                  }
                }));
    bossBarManager = new BossBarManager(this, storageManager, lang);
    loadTestService = new LoadTestService(this, database, bossBarManager, lang);
    cacheDebugService = new CacheDebugService(this);
    pickDebugService = new PickDebugService();
    worldEditDebugService = new WorldEditDebugService(this);
    embeddedChorusfix =
        new EmbeddedChorusfixController(
            this,
            () -> EmbeddedChorusfixConfig.from(getConfig()),
            this::isChorusUpdatesDisabled,
            () -> wireMaterial,
            this::isExortChorusCarrier);
    metrics = ExortMetrics.create(this);
    return true;
  }

  @Override
  public void onDisable() {
    if (runtimeTasks != null) {
      runtimeTasks.cancel();
    }
    closeRuntimeSessions();
    if (storageManager != null) {
      storageManager.flushAllAndWait();
    }
    if (bossBarManager != null) {
      bossBarManager.clearAll();
    }
    if (loadTestService != null) {
      loadTestService.stop(false);
    }
    if (metrics != null) {
      metrics.shutdown();
    }
    stopEmbeddedChorusfix();
    closeRuntimeHandle();
    stopBusService();
    stopRecipeService();
    stopDisplayState();
    stopResourcePackService();
    if (nexoResourcePackIntegration != null) {
      nexoResourcePackIntegration.clearRegistration();
    }
    if (oraxenResourcePackIntegration != null) {
      oraxenResourcePackIntegration.clearRegistration();
    }
    busSessionManager = null;
    DisplayBreakAnimationSender.clearStaleOverlays();
    if (database != null) {
      database.close();
    }
    runtimeMaterials = null;
    blockClassifier = null;
  }

  private void stopResourcePackService() {
    if (resourcePackService == null) {
      return;
    }
    resourcePackService.stop();
    resourcePackService = null;
  }

  private void stopRecipeService() {
    if (recipeService == null) {
      return;
    }
    recipeService.unregisterAll();
    recipeService = null;
  }

  private void stopDisplayState() {
    if (blockProxyService != null) {
      blockProxyService.stop();
      blockProxyService = null;
    }
    if (displayCullingService != null) {
      displayCullingService.stop();
      displayCullingService = null;
    }
    if (hologramManager != null) {
      hologramManager.stop();
    }
    if (monitorDisplayManager != null) {
      monitorDisplayManager.stop();
    }
  }

  private void stopBusService() {
    if (busService == null) {
      return;
    }
    busService.stop();
    busService = null;
  }

  private void recordPickDebug(String message) {
    if (pickDebugService != null) {
      pickDebugService.record(message);
    }
  }

  private void recordPickDebugFull(String message) {
    if (pickDebugService != null) {
      pickDebugService.recordFull(message);
    }
  }

  @Override
  public String getVersion() {
    return getPluginMeta().getVersion();
  }

  @Override
  public Optional<StorageTierDescriptor> getStorageTier(String key) {
    return StorageTier.fromString(key).map(StorageTier::descriptor);
  }

  @Override
  public Collection<StorageTierDescriptor> getStorageTiers() {
    return StorageTier.allTiers().stream().map(StorageTier::descriptor).toList();
  }

  @Override
  public boolean isExortBlock(Block block) {
    CarrierMaterials materials = runtimeMaterials;
    ExortBlockClassifier classifier = blockClassifier;
    return materials != null && classifier != null && classifier.isExortBlock(block);
  }

  @Override
  public boolean isExortChorusCarrier(Block block) {
    CarrierMaterials materials = runtimeMaterials;
    ExortBlockClassifier classifier = blockClassifier;
    return materials != null && classifier != null && classifier.isExortChorusCarrier(block);
  }

  private boolean ensureMinMinecraftVersion() {
    String version = Bukkit.getMinecraftVersion();
    var parsed = MinecraftVersionRequirement.Version.parse(version);
    if (parsed.isEmpty()) {
      ExortLog.warn("Unable to parse Minecraft version '" + version + "'. Proceeding anyway.");
      return true;
    }
    if (!MIN_MC_VERSION.accepts(parsed.get())) {
      ExortLog.error(
          "Minecraft " + MIN_MC_VERSION.displayName() + " is required. Current: " + version);
      return false;
    }
    return true;
  }

  public CompletableFuture<ItemNameService.Status> reloadRuntime() {
    boolean wasResourceMode = resourceMode;
    FileConfiguration previousConfig =
        activeRuntimeConfig == null ? getConfig() : activeRuntimeConfig;
    RuntimeModeState previousMode = currentModeState();
    RuntimeHandle<ExortRuntimeServices> previousHandle = runtimeHandle;
    if (previousHandle == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("No active runtime is available to reload"));
    }
    if (!ConfigUpdater.update(this, "config.yml")) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("config.yml update failed; runtime was not reloaded"));
    }
    if (!ensureStorageTiersFile()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("storage-tiers.yml update failed; runtime was not reloaded"));
    }
    ensureRecipesFile();
    FileConfiguration candidateConfig;
    try {
      YamlConfiguration loaded = new YamlConfiguration();
      loaded.load(new File(getDataFolder(), "config.yml"));
      loaded.setDefaults(previousConfig.getDefaults());
      candidateConfig = loaded;
    } catch (Exception error) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("config.yml is invalid; runtime was not reloaded", error));
    }
    RuntimeModeState candidateMode = evaluateModePolicy(candidateConfig);
    try {
      ExortRuntimeFactory.preflight(
          this,
          candidateConfig,
          candidateMode.resourceMode(),
          candidateMode.resourceWireUsesBarrier());
    } catch (RuntimeException | LinkageError preflightFailure) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Runtime preflight failed; the active runtime was not changed", preflightFailure));
    }
    closeRuntimeSessions();
    boolean switchedToResourceMode = !wasResourceMode && candidateMode.resourceMode();
    runtimeHandle = null;
    RuntimeReloadCoordinator.Outcome<ExortRuntimeServices> outcome =
        RuntimeReloadCoordinator.replace(
            previousHandle,
            () -> createRuntimeHandle(false, candidateConfig, candidateMode),
            services -> publishRuntime(services, candidateConfig),
            () -> createRuntimeHandle(false, previousConfig, previousMode),
            services -> publishRuntime(services, previousConfig));
    runtimeHandle = outcome.handle();
    if (outcome.failure() != null) {
      applyModePolicy(previousMode);
      if (outcome.restored()) {
        outcome.handle().value().itemNamesStatus();
      }
      if (outcome.fatal()) {
        getLogger()
            .log(
                Level.SEVERE,
                "Runtime reload could not be rolled back safely; disabling Exort",
                outcome.failure());
        getServer().getPluginManager().disablePlugin(this);
      }
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              outcome.restored()
                  ? "Runtime activation failed; last-known-good runtime was restored"
                  : "Runtime activation failed and safe rollback was not possible",
              outcome.failure()));
    }
    applyModePolicy(candidateMode);
    CompletableFuture<ItemNameService.Status> future = outcome.handle().value().itemNamesStatus();
    reloadConfig();
    activeRuntimeConfig = getConfig();
    reloadResourcePackService();
    if (switchedToResourceMode && resourcePackService != null) {
      resourcePackService.requestSendOnlineWhenReady();
    }
    scheduleEmbeddedChorusfixFinalLog();
    return future;
  }

  private void closeRuntimeSessions() {
    if (sessionManager != null) {
      sessionManager.allSessions().stream()
          .toList()
          .forEach(session -> sessionManager.forceCloseSession(session.getViewer()));
    }
    if (busSessionManager != null) {
      busSessionManager.shutdown();
      busSessionManager = null;
    }
    if (transmitterSessionManager != null) {
      transmitterSessionManager.shutdown();
      transmitterSessionManager = null;
    }
  }

  public void reloadResourcePackService() {
    if (resourcePackService != null) {
      resourcePackService.reload();
    }
  }

  private void closeRuntimeHandle() {
    RuntimeHandle<ExortRuntimeServices> current = runtimeHandle;
    runtimeHandle = null;
    if (loadTestService != null) {
      loadTestService.clearRuntimeDependencies();
    }
    if (current == null) {
      return;
    }
    try {
      current.close();
    } catch (RuntimeException error) {
      getLogger().log(Level.WARNING, "One or more runtime resources failed to close", error);
    }
  }

  private void unregisterReloadableRuntimeListeners() {
    HandlerList.unregisterAll(this);
    if (resourcePackService != null) {
      Bukkit.getPluginManager().registerEvents(resourcePackService, this);
    }
    if (playerLocaleService != null) {
      Bukkit.getPluginManager().registerEvents(playerLocaleService, this);
    }
    if (nexoResourcePackIntegration != null) {
      nexoResourcePackIntegration.clearRegistration();
    }
    if (oraxenResourcePackIntegration != null) {
      oraxenResourcePackIntegration.clearRegistration();
    }
    registerChorusfixIntegrationWatcher();
    registerNexoResourcePackIntegrationWatcher();
    registerOraxenResourcePackIntegrationWatcher();
    registerProtectionLifecycleWatcher();
  }

  private CompletableFuture<ItemNameService.Status> registerRuntime(
      boolean refreshItemDictionaries) {
    return registerRuntime(
        refreshItemDictionaries, activeRuntimeConfig == null ? getConfig() : activeRuntimeConfig);
  }

  private CompletableFuture<ItemNameService.Status> registerRuntime(
      boolean refreshItemDictionaries, FileConfiguration config) {
    RuntimeModeState mode = evaluateModePolicy(config);
    ExortRuntimeFactory.preflight(
        this, config, mode.resourceMode(), mode.resourceWireUsesBarrier());
    FileConfiguration previousConfig = activeRuntimeConfig;
    RuntimeHandle<ExortRuntimeServices> candidate =
        ExortRuntimeFactory.create(
            createRuntimeFactoryDependencies(config), refreshItemDictionaries);
    ExortRuntimeServices services = candidate.value();
    try {
      applyRuntimeServices(services);
      runtimeHandle = candidate;
      activeRuntimeConfig = config;
      refreshEmbeddedChorusfix();
      return services.itemNamesStatus();
    } catch (RuntimeException | LinkageError error) {
      activeRuntimeConfig = previousConfig;
      candidate.close();
      throw error;
    }
  }

  private RuntimeHandle<ExortRuntimeServices> createRuntimeHandle(
      boolean refreshItemDictionaries, FileConfiguration config, RuntimeModeState mode) {
    applyModePolicy(mode);
    return ExortRuntimeFactory.create(
        createRuntimeFactoryDependencies(config), refreshItemDictionaries);
  }

  private void publishRuntime(ExortRuntimeServices services, FileConfiguration config) {
    applyRuntimeServices(services);
    activeRuntimeConfig = config;
    refreshEmbeddedChorusfix();
  }

  private ExortRuntimeFactoryDependencies createRuntimeFactoryDependencies(
      FileConfiguration config) {
    return new ExortRuntimeFactoryDependencies(
        new CoreServices(
            this,
            lang,
            itemNameService,
            searchDialogService,
            keys,
            storageManager,
            database,
            sessionManager,
            bossBarManager,
            playerFeedback,
            inventoryRefreshService,
            runtimeTasks),
        new RuntimeConfigSnapshot(
            config,
            resourceMode,
            resourceWireUsesBarrier,
            () -> GuiRuntimeConfig.fromConfig(config),
            GuiOverlayConfig::defaults),
        new RuntimeIntegrationContext(
            () -> networkGraphCache,
            () -> regionProtection,
            () -> worldEditDebugService,
            () -> busService,
            recipeService,
            WorldEditIntegration::tryRegister,
            ignored -> {}),
        new RuntimeHooks(
            () -> reloadDefaultSortMode(config),
            this::unregisterReloadableRuntimeListeners,
            () -> setupRegionProtection(config),
            () -> {
              if (sessionManager != null) {
                sessionManager.revalidateSessions();
              }
            },
            this::recordPickDebug,
            this::recordPickDebugFull,
            block -> {
              if (placementTracker != null) {
                placementTracker.markPlaced(block);
              }
            },
            block -> placementTracker != null && placementTracker.isRecentlyPlaced(block),
            block -> {
              if (placementTracker != null) {
                placementTracker.markPlaced(block);
              }
            },
            block -> placementTracker != null && placementTracker.isRecentlyPlaced(block),
            storageId -> sessionManager.renderStorage(storageId, SortEvent.NONE)));
  }

  private void applyRuntimeServices(ExortRuntimeServices services) {
    customItems = services.customItems();
    wirelessService = services.wirelessService();
    wirelessTransmitterService = services.wirelessTransmitterService();
    transmitterSessionManager = services.transmitterSessionManager();
    chunkLoaderService = services.chunkLoaderService();
    CarrierMaterials materials = services.materials();
    runtimeMaterials = materials;
    blockClassifier = new ExortBlockClassifier(this, materials);
    wireMaterial = materials.wire();
    storageCarrier = materials.storageCarrier();
    relayTraversalCarrier = services.relayTraversalCarrier();
    terminalCarrier = materials.terminalCarrier();
    wireLimit = services.wireLimit();
    wireHardCap = services.wireHardCap();
    relayRangeChunks = services.relayRangeChunks();
    hologramManager = services.hologramManager();
    monitorDisplayManager = services.monitorDisplayManager();
    blockProxyService = services.blockProxyService();
    displayCullingService = services.displayCullingService();
    busService = services.busService();
    busSessionManager = services.busSessionManager();
    craftingRules = services.craftingRules();
    recipeService = services.recipeService();
    if (playerLocaleService != null) {
      Bukkit.getOnlinePlayers().forEach(playerLocaleService::preloadItemDictionary);
    }
    if (loadTestService != null) {
      loadTestService.setRuntimeDependencies(
          new LoadTestRuntimeDependencies(
              keys,
              storageManager,
              services.displayRefreshService(),
              services.busService(),
              networkGraphCache,
              materials,
              services.hologramManager(),
              services.monitorDisplayManager(),
              services.wireLimit(),
              services.wireHardCap(),
              services.relayTraversalCarrier(),
              services.relayRangeChunks()));
    }
  }

  private RuntimeModeState evaluateModePolicy(FileConfiguration config) {
    return RuntimeModeCoordinator.evaluate(
        config.getString("mode", ModePolicy.DEFAULT_MODE),
        WireCarrierMode.fromConfig(config),
        this::isChorusUpdatesDisabled,
        MODE_FIX_RESOURCE_COMMAND);
  }

  private void applyModePolicy(RuntimeModeState state) {
    configuredMode = state.configuredMode();
    resourceMode = state.resourceMode();
    resourceWireUsesBarrier = state.resourceWireUsesBarrier();
    resourceWireCarrierFallback = state.resourceWireCarrierFallback();
  }

  private RuntimeModeState currentModeState() {
    return new RuntimeModeState(
        configuredMode, resourceMode, resourceWireUsesBarrier, resourceWireCarrierFallback);
  }

  static List<String> resourceWireCarrierWarningLines() {
    return RuntimeModeCoordinator.resourceWireCarrierWarningLines(MODE_FIX_RESOURCE_COMMAND);
  }

  public PaperChorusPlantUpdates.Status chorusPlantUpdateStatus() {
    return PaperChorusPlantUpdates.read(serverRoot());
  }

  public PaperChorusPlantUpdates.FixResult disableChorusPlantUpdatesInPaperConfig() {
    return PaperChorusPlantUpdates.disable(serverRoot());
  }

  public EmbeddedChorusfixStatus chorusfixStatus() {
    return embeddedChorusfix == null
        ? EmbeddedChorusfixStatus.INACTIVE
        : embeddedChorusfix.status();
  }

  private boolean isChorusUpdatesDisabled() {
    return chorusPlantUpdateStatus().disabled();
  }

  private File serverRoot() {
    File pluginsDir = pluginsDir();
    return pluginsDir != null ? pluginsDir.getParentFile() : null;
  }

  private File pluginsDir() {
    return getDataFolder().getParentFile();
  }

  private void refreshEmbeddedChorusfix() {
    if (embeddedChorusfix != null) {
      embeddedChorusfix.refresh();
    }
  }

  private void scheduleEmbeddedChorusfixFinalLog() {
    Bukkit.getScheduler().runTask(this, this::logEmbeddedChorusfixFinalState);
  }

  private void logEmbeddedChorusfixFinalState() {
    if (embeddedChorusfix != null) {
      embeddedChorusfix.announceEmbeddedIfActive();
      embeddedChorusfix.warnIfBlockedByProvider();
    }
  }

  private void stopEmbeddedChorusfix() {
    if (embeddedChorusfix != null) {
      embeddedChorusfix.stop();
    }
  }

  private void registerChorusfixIntegrationWatcher() {
    logChorusfixIntegrationIfEnabled(
        Bukkit.getPluginManager().getPlugin(ChorusfixIntegration.PLUGIN_NAME));
    Bukkit.getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onPluginEnable(PluginEnableEvent event) {
                String pluginName = event.getPlugin().getName();
                boolean chorusfixEnabled = ChorusfixIntegration.PLUGIN_NAME.equals(pluginName);
                boolean knownProviderEnabled =
                    embeddedChorusfix != null && embeddedChorusfix.isKnownProvider(pluginName);
                if (!chorusfixEnabled && !knownProviderEnabled) {
                  return;
                }
                if (chorusfixEnabled) {
                  logChorusfixIntegrationIfEnabled(event.getPlugin());
                }
                refreshEmbeddedChorusfix();
                if ("ItemsAdder".equals(pluginName)) {
                  reloadResourcePackService();
                }
              }

              @EventHandler
              public void onPluginDisable(PluginDisableEvent event) {
                String pluginName = event.getPlugin().getName();
                boolean chorusfixDisabled = ChorusfixIntegration.PLUGIN_NAME.equals(pluginName);
                boolean knownProviderDisabled =
                    embeddedChorusfix != null && embeddedChorusfix.isKnownProvider(pluginName);
                if (!chorusfixDisabled && !knownProviderDisabled) {
                  return;
                }
                if (chorusfixDisabled) {
                  chorusfixIntegrationLogged = false;
                }
                refreshEmbeddedChorusfix();
                if ("ItemsAdder".equals(pluginName)) {
                  reloadResourcePackService();
                }
              }
            },
            this);
  }

  private void registerOraxenResourcePackIntegrationWatcher() {
    registerOraxenResourcePackIntegrationIfEnabled(
        Bukkit.getPluginManager().getPlugin(OraxenResourcePackIntegration.PLUGIN_NAME));
    Bukkit.getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onPluginEnable(PluginEnableEvent event) {
                if (!OraxenResourcePackIntegration.PLUGIN_NAME.equals(
                    event.getPlugin().getName())) {
                  return;
                }
                if (registerOraxenResourcePackIntegrationIfEnabled(event.getPlugin())) {
                  reloadResourcePackService();
                }
              }

              @EventHandler
              public void onPluginDisable(PluginDisableEvent event) {
                if (!OraxenResourcePackIntegration.PLUGIN_NAME.equals(
                    event.getPlugin().getName())) {
                  return;
                }
                if (oraxenResourcePackIntegration != null) {
                  oraxenResourcePackIntegration.clearRegistration();
                }
                reloadResourcePackService();
              }
            },
            this);
  }

  private boolean registerOraxenResourcePackIntegrationIfEnabled(org.bukkit.plugin.Plugin plugin) {
    if (oraxenResourcePackIntegration == null) {
      return false;
    }
    return oraxenResourcePackIntegration.registerIfEnabled(this, plugin);
  }

  private void registerNexoResourcePackIntegrationWatcher() {
    registerNexoResourcePackIntegrationIfAvailable(
        Bukkit.getPluginManager().getPlugin(NexoResourcePackIntegration.PLUGIN_NAME));
    Bukkit.getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onPluginEnable(PluginEnableEvent event) {
                if (!NexoResourcePackIntegration.PLUGIN_NAME.equals(event.getPlugin().getName())) {
                  return;
                }
                if (registerNexoResourcePackIntegrationIfAvailable(event.getPlugin())) {
                  reloadResourcePackService();
                }
              }

              @EventHandler
              public void onPluginDisable(PluginDisableEvent event) {
                if (!NexoResourcePackIntegration.PLUGIN_NAME.equals(event.getPlugin().getName())) {
                  return;
                }
                if (nexoResourcePackIntegration != null) {
                  nexoResourcePackIntegration.clearRegistration();
                }
                reloadResourcePackService();
              }
            },
            this);
  }

  private boolean registerNexoResourcePackIntegrationIfAvailable(org.bukkit.plugin.Plugin plugin) {
    if (nexoResourcePackIntegration == null || nexoResourcePackIntegration.isRegistered()) {
      return nexoResourcePackIntegration != null && nexoResourcePackIntegration.isRegistered();
    }
    return nexoResourcePackIntegration.registerIfAvailable(this, plugin);
  }

  private void logChorusfixIntegrationIfEnabled(org.bukkit.plugin.Plugin plugin) {
    if (chorusfixIntegrationLogged || plugin == null || !plugin.isEnabled()) {
      return;
    }
    chorusfixIntegrationLogged = true;
    ExortLog.success(ChorusfixIntegration.enabledMessage(plugin));
  }

  private void setupRegionProtection() {
    setupRegionProtection(activeRuntimeConfig == null ? getConfig() : activeRuntimeConfig);
  }

  private void setupRegionProtection(FileConfiguration config) {
    regionProtection.setDelegate(RegionProtection.allowAll());
    ProtectionRuntimeConfig protectionConfig = ProtectionRuntimeConfig.fromConfig(config);
    if (!protectionConfig.enabled()) {
      protectionStatus = ProtectionStatus.disabledByConfig();
      ExortLog.info(ProtectionStartupLog.disabledByConfig());
      return;
    }
    boolean failClosed = protectionConfig.failClosedOnError();
    if (!failClosed) {
      ExortLog.warn(ProtectionStartupLog.failOpenOverride());
    }
    ProtectionRuntimeConfig.Adapters enabledAdapters = protectionConfig.adapters();
    List<CompositeRegionProtection.Adapter> adapters = new ArrayList<>();
    Set<String> missingPlugins = new LinkedHashSet<>();
    Set<String> failedAdapters = new LinkedHashSet<>();

    if (enabledAdapters.worldGuard()
        && !addProtectionAdapter(
            adapters,
            missingPlugins,
            failedAdapters,
            "WorldGuard",
            () ->
                createProtectionAdapter(
                    "com.zxcmc.exort.integration.protection.WorldGuardProtection"),
            failClosed)) {
      return;
    }
    if (enabledAdapters.griefPrevention()
        && !addProtectionAdapter(
            adapters,
            missingPlugins,
            failedAdapters,
            "GriefPrevention",
            () ->
                createProtectionAdapter(
                    "com.zxcmc.exort.integration.protection.GriefPreventionProtection"),
            failClosed)) {
      return;
    }
    if (enabledAdapters.towny()
        && !addProtectionAdapter(
            adapters,
            missingPlugins,
            failedAdapters,
            "Towny",
            () -> createProtectionAdapter("com.zxcmc.exort.integration.protection.TownyProtection"),
            failClosed)) {
      return;
    }
    if (enabledAdapters.lands()
        && !addProtectionAdapter(
            adapters,
            missingPlugins,
            failedAdapters,
            "Lands",
            () ->
                createProtectionAdapter(
                    "com.zxcmc.exort.integration.protection.LandsProtection", this),
            failClosed)) {
      return;
    }
    if (enabledAdapters.residence()
        && !addProtectionAdapter(
            adapters,
            missingPlugins,
            failedAdapters,
            "Residence",
            () ->
                createProtectionAdapter(
                    "com.zxcmc.exort.integration.protection.ResidenceProtection"),
            failClosed)) {
      return;
    }

    if (adapters.isEmpty()) {
      protectionStatus =
          failedAdapters.isEmpty()
              ? ProtectionStatus.noProvider(failClosed, missingPlugins)
              : ProtectionStatus.active(failClosed, List.of(), missingPlugins, failedAdapters);
      ExortLog.info(ProtectionStartupLog.noSupportedProvider());
      return;
    }

    CompositeRegionProtection composite =
        new CompositeRegionProtection(adapters, getLogger(), failClosed);
    regionProtection.setDelegate(composite);
    protectionStatus =
        ProtectionStatus.active(
            failClosed, composite.adapterNames(), missingPlugins, failedAdapters);
    ExortLog.success(ProtectionStartupLog.enabled(composite.adapterNames()));
  }

  private boolean addProtectionAdapter(
      List<CompositeRegionProtection.Adapter> adapters,
      Set<String> missingPlugins,
      Set<String> failedAdapters,
      String pluginName,
      ProtectionFactory factory,
      boolean failClosed) {
    var plugin = getServer().getPluginManager().getPlugin(pluginName);
    if (plugin == null || !plugin.isEnabled()) {
      missingPlugins.add(pluginName);
      return true;
    }
    try {
      adapters.add(new CompositeRegionProtection.Adapter(pluginName, factory.create()));
      return true;
    } catch (RuntimeException | LinkageError error) {
      failedAdapters.add(pluginName);
      getLogger()
          .log(
              Level.WARNING,
              pluginName
                  + " is enabled, but Exort could not initialize its protection adapter; "
                  + (failClosed ? "denying Exort actions." : "allowing Exort actions."),
              error);
      if (failClosed) {
        regionProtection.setDelegate(RegionProtection.denyAll());
        protectionStatus =
            ProtectionStatus.degradedFailClosed(
                true, adapterNames(adapters), missingPlugins, failedAdapters);
        return false;
      }
      return true;
    }
  }

  private ProtectionStatus currentProtectionStatus() {
    ProtectionStatus status = protectionStatus;
    if (regionProtection.delegate() instanceof CompositeRegionProtection composite) {
      status = status.withRuntimeFailures(composite.runtimeFailureKeys());
    }
    return status;
  }

  private static List<String> adapterNames(List<CompositeRegionProtection.Adapter> adapters) {
    List<String> names = new ArrayList<>(adapters.size());
    for (CompositeRegionProtection.Adapter adapter : adapters) {
      names.add(adapter.name());
    }
    return names;
  }

  private RegionProtection createProtectionAdapter(String className, Object... args) {
    try {
      Class<?> type = Class.forName(className, true, getClassLoader());
      if (args.length == 0) {
        return (RegionProtection) type.getDeclaredConstructor().newInstance();
      }
      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (parameterTypes.length != args.length) {
          continue;
        }
        boolean matches = true;
        for (int i = 0; i < args.length; i++) {
          if (!parameterTypes[i].isAssignableFrom(args[i].getClass())) {
            matches = false;
            break;
          }
        }
        if (matches) {
          constructor.setAccessible(true);
          return (RegionProtection) constructor.newInstance(args);
        }
      }
      throw new IllegalStateException("No matching constructor for " + className);
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("Could not create protection adapter " + className, error);
    }
  }

  private void registerProtectionLifecycleWatcher() {
    Bukkit.getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onPluginEnable(PluginEnableEvent event) {
                if (!isProtectionProvider(event.getPlugin().getName())) {
                  return;
                }
                setupRegionProtection();
              }

              @EventHandler
              public void onPluginDisable(PluginDisableEvent event) {
                if (!isProtectionProvider(event.getPlugin().getName())) {
                  return;
                }
                setupRegionProtection();
              }
            },
            this);
  }

  private static boolean isProtectionProvider(String pluginName) {
    return switch (pluginName) {
      case "WorldGuard", "GriefPrevention", "Towny", "Lands", "Residence" -> true;
      default -> false;
    };
  }

  private interface ProtectionFactory {
    RegionProtection create();
  }

  private void registerBrigadierCommands() {
    getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event -> {
              event
                  .registrar()
                  .register(
                      new ExortBrigadier(createBrigadierDependencies()).build(),
                      "Exort Storage Network admin commands",
                      List.of("esn", "vst"));
            });
  }

  private ExortBrigadierDependencies createBrigadierDependencies() {
    return new ExortBrigadierDependencies(
        this,
        lang,
        new CommandRuntimeAccess(
            () -> customItems,
            () -> wirelessService,
            () -> chunkLoaderService,
            () -> networkGraphCache),
        keys,
        storageManager,
        database,
        sessionManager,
        cacheDebugService,
        worldEditDebugService,
        pickDebugService,
        () -> displayCullingService,
        loadTestService,
        itemNameService,
        () -> resourcePackService,
        this::reloadResourcePackService,
        this::reloadRuntime,
        () -> getConfig().getString("language", "en_us"),
        value -> {
          getConfig().set("language", value);
          saveConfig();
        },
        value -> {
          getConfig().set("mode", value);
          saveConfig();
        },
        () -> configuredMode,
        () -> resourceMode ? "RESOURCE" : "VANILLA",
        () -> resourceWireCarrierFallback,
        () -> getPluginMeta().getVersion(),
        this::disableChorusPlantUpdatesInPaperConfig,
        this::chorusfixStatus,
        () -> StorageRuntimeConfig.fromConfig(getConfig()).cacheIdleUnloadSeconds(),
        () -> wireLimit,
        () -> wireHardCap,
        () -> relayRangeChunks,
        () -> wireMaterial,
        () -> storageCarrier,
        () -> relayTraversalCarrier,
        this::currentProtectionStatus);
  }

  private void reloadDefaultSortMode(FileConfiguration config) {
    defaultSortModeName = StorageRuntimeConfig.fromConfig(config).defaultSortModeName();
  }

  private boolean ensureStorageTiersFile() {
    return ConfigUpdater.update(this, "storage-tiers.yml");
  }

  private void ensureRecipesFile() {
    File file = new File(getDataFolder(), "recipes.yml");
    if (file.exists()) return;
    saveResource("recipes.yml", false);
  }
}
