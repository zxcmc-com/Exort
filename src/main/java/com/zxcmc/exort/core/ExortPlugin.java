package com.zxcmc.exort.core;

import com.zxcmc.exort.api.ExortApi;
import com.zxcmc.exort.api.model.StorageTierDescriptor;
import com.zxcmc.exort.breaking.CustomBlockBreaker;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.carrier.WireCarrierMode;
import com.zxcmc.exort.command.ExortBrigadier;
import com.zxcmc.exort.command.ExortBrigadierDependencies;
import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.debug.LoadTestRuntimeDependencies;
import com.zxcmc.exort.debug.LoadTestService;
import com.zxcmc.exort.debug.PickDebugService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.DisplayBreakAnimationSender;
import com.zxcmc.exort.display.DisplayCullingService;
import com.zxcmc.exort.display.ExortBlockProxyService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.MonitorDisplayManager;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import com.zxcmc.exort.gui.SearchDialogService;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.gui.SessionManagerDependencies;
import com.zxcmc.exort.gui.SortEvent;
import com.zxcmc.exort.gui.SortMode;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.i18n.PlayerLocaleService;
import com.zxcmc.exort.infra.config.ConfigUpdater;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.metrics.ExortMetrics;
import com.zxcmc.exort.infra.resourcepack.ResourcePackService;
import com.zxcmc.exort.infra.update.UpdateChecker;
import com.zxcmc.exort.integration.protection.CompositeRegionProtection;
import com.zxcmc.exort.integration.protection.ProtectionRuntimeConfig;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.integration.worldedit.WorldEditIntegration;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.items.InventoryRefreshService;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.monitor.MonitorPlacementTracker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.NetworkGraphCacheProvider;
import com.zxcmc.exort.placement.RightClickPlacementGuard;
import com.zxcmc.exort.platform.MinecraftVersionRequirement;
import com.zxcmc.exort.platform.ModePolicy;
import com.zxcmc.exort.platform.PaperChorusPlantUpdates;
import com.zxcmc.exort.platform.RuntimeModeCoordinator;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.runtime.ExortRuntimeFactory;
import com.zxcmc.exort.runtime.ExortRuntimeFactoryDependencies;
import com.zxcmc.exort.runtime.ExortRuntimeServices;
import com.zxcmc.exort.runtime.RuntimeMaterials;
import com.zxcmc.exort.runtime.RuntimeTaskScheduler;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageRuntimeConfig;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.text.ExortText;
import com.zxcmc.exort.wireless.WirelessTerminalService;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ExortPlugin extends JavaPlugin implements ExortApi, NetworkGraphCacheProvider {
  static final String MODE_FIX_RESOURCE_COMMAND = "/exort mode fix RESOURCE";
  private static final MinecraftVersionRequirement MIN_MC_VERSION =
      MinecraftVersionRequirement.atLeast(1, 21, 7);
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
  private BossBarManager bossBarManager;
  private SearchDialogService searchDialogService;
  private InventoryRefreshService inventoryRefreshService;
  private MonitorPlacementTracker monitorPlacementTracker;
  private RuntimeTaskScheduler runtimeTasks;
  private int wireLimit;
  private int wireHardCap;
  private Material wireMaterial;
  private Material storageCarrier;
  private Material terminalCarrier;
  private ItemHologramManager hologramManager;
  private boolean dialogSupported;
  private MonitorDisplayManager monitorDisplayManager;
  private ExortBlockProxyService blockProxyService;
  private DisplayCullingService displayCullingService;
  private BusService busService;
  private BusSessionManager busSessionManager;
  private CustomBlockBreaker customBlockBreaker;
  private CraftingRules craftingRules;
  private RecipeService recipeService;
  private LoadTestService loadTestService;
  private CacheDebugService cacheDebugService;
  private PickDebugService pickDebugService;
  private WorldEditDebugService worldEditDebugService;
  private Metrics metrics;
  private WorldEditIntegration worldEditIntegration;
  private ResourcePackService resourcePackService;
  private PacketEnhancements packetEnhancements;
  private RightClickPlacementGuard placementGuard;
  private NetworkGraphCache networkGraphCache;
  private boolean resourceMode;
  private boolean resourceWireUsesBarrier;
  private boolean resourceWireCarrierFallback;
  private String configuredMode = "RESOURCE";
  private volatile String defaultSortModeName = SortMode.AMOUNT.name();
  private RegionProtection regionProtection = RegionProtection.allowAll();

  @Override
  public void onEnable() {
    if (!ensureMinMinecraftVersion()) {
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    prepareConfigFiles();
    new UpdateChecker(this).checkAsync();
    evaluateModePolicy();
    if (!initCoreServices()) {
      return;
    }
    registerRuntime(true);
    reloadResourcePackService();
    registerBrigadierCommands();
  }

  private void prepareConfigFiles() {
    saveDefaultConfig();
    ConfigUpdater.update(this, "config.yml");
    reloadConfig();
    ensureStorageTiersFile();
    ensureRecipesFile();
  }

  private boolean initCoreServices() {
    lang = new Lang(this);
    itemNameService = new ItemNameService(this);
    resourcePackService =
        new ResourcePackService(this, () -> resourceMode, lang::tr, ExortText::configRichText);
    Bukkit.getPluginManager().registerEvents(resourcePackService, this);
    playerFeedback = new PlayerFeedback(lang);
    playerLocaleService =
        new PlayerLocaleService(
            this, lang, itemNameService, () -> sessionManager, () -> busSessionManager);
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
        new RuntimeTaskScheduler(
            this, () -> storageManager, () -> StorageRuntimeConfig.fromConfig(getConfig()));
    inventoryRefreshService = new InventoryRefreshService(() -> customItems, () -> wirelessService);
    monitorPlacementTracker = new MonitorPlacementTracker();
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
                () -> busService,
                () -> craftingRules,
                () -> resourceMode,
                () -> dialogSupported,
                () -> wireLimit,
                () -> wireHardCap,
                () -> wireMaterial,
                () -> storageCarrier,
                () -> terminalCarrier,
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
    stopReloadableRuntime();
    stopBusService();
    stopRecipeService();
    stopDisplayState();
    stopResourcePackService();
    busSessionManager = null;
    DisplayBreakAnimationSender.clearStaleOverlays();
    if (database != null) {
      database.close();
    }
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
    ConfigUpdater.update(this, "config.yml");
    reloadConfig();
    closeRuntimeSessions();
    ensureStorageTiersFile();
    ensureRecipesFile();
    evaluateModePolicy();
    CompletableFuture<ItemNameService.Status> future = registerRuntime(false);
    reloadResourcePackService();
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
  }

  public void reloadResourcePackService() {
    if (resourcePackService != null) {
      resourcePackService.reload();
    }
  }

  private void stopPacketEnhancements() {
    if (packetEnhancements == null) {
      return;
    }
    packetEnhancements.unregister();
    packetEnhancements = null;
  }

  private void stopPlacementGuard() {
    if (placementGuard == null) {
      return;
    }
    placementGuard.stop();
    placementGuard = null;
  }

  private void stopWorldEditIntegration() {
    if (worldEditIntegration == null) {
      return;
    }
    worldEditIntegration.shutdown();
    worldEditIntegration = null;
  }

  private void stopReloadableRuntime() {
    if (loadTestService != null) {
      loadTestService.clearRuntimeDependencies();
    }
    stopWorldEditIntegration();
    stopPlacementGuard();
    stopPacketEnhancements();
    if (customBlockBreaker != null) {
      customBlockBreaker.shutdown();
      customBlockBreaker = null;
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
  }

  private void resetReloadableDisplayState() {
    stopBusService();
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
    monitorDisplayManager = null;
  }

  private CompletableFuture<ItemNameService.Status> registerRuntime(
      boolean refreshItemDictionaries) {
    ExortRuntimeServices services =
        ExortRuntimeFactory.create(createRuntimeFactoryDependencies(), refreshItemDictionaries);
    applyRuntimeServices(services);
    return services.itemNamesStatus();
  }

  private ExortRuntimeFactoryDependencies createRuntimeFactoryDependencies() {
    return new ExortRuntimeFactoryDependencies(
        this,
        getConfig(),
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
        () -> networkGraphCache,
        () -> regionProtection,
        () -> worldEditDebugService,
        () -> busService,
        recipeService,
        runtimeTasks,
        resourceMode,
        resourceWireUsesBarrier,
        this::reloadDefaultSortMode,
        this::stopReloadableRuntime,
        this::unregisterReloadableRuntimeListeners,
        this::setupRegionProtection,
        this::resetReloadableDisplayState,
        () -> {
          if (sessionManager != null) {
            sessionManager.revalidateSessions();
          }
        },
        this::recordPickDebug,
        this::recordPickDebugFull,
        block -> {
          if (monitorPlacementTracker != null) {
            monitorPlacementTracker.markPlaced(block);
          }
        },
        block -> monitorPlacementTracker != null && monitorPlacementTracker.isRecentlyPlaced(block),
        () -> GuiRuntimeConfig.fromConfig(getConfig()),
        GuiOverlayConfig::defaults,
        storageId -> sessionManager.renderStorage(storageId, SortEvent.NONE),
        WorldEditIntegration::tryRegister,
        integration -> worldEditIntegration = integration);
  }

  private void applyRuntimeServices(ExortRuntimeServices services) {
    customItems = services.customItems();
    wirelessService = services.wirelessService();
    RuntimeMaterials materials = services.materials();
    wireMaterial = materials.wire();
    storageCarrier = materials.storageCarrier();
    terminalCarrier = materials.terminalCarrier();
    wireLimit = services.wireLimit();
    wireHardCap = services.wireHardCap();
    hologramManager = services.hologramManager();
    monitorDisplayManager = services.monitorDisplayManager();
    blockProxyService = services.blockProxyService();
    displayCullingService = services.displayCullingService();
    busService = services.busService();
    busSessionManager = services.busSessionManager();
    customBlockBreaker = services.customBlockBreaker();
    craftingRules = services.craftingRules();
    recipeService = services.recipeService();
    packetEnhancements = services.packetEnhancements();
    placementGuard = services.placementGuard();
    worldEditIntegration = services.worldEditIntegration();
    dialogSupported = services.dialogSupported();
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
              services.wireHardCap()));
    }
  }

  private void evaluateModePolicy() {
    var state =
        RuntimeModeCoordinator.evaluate(
            getConfig().getString("mode", ModePolicy.DEFAULT_MODE),
            WireCarrierMode.fromConfig(getConfig()),
            this::isChorusUpdatesDisabled,
            MODE_FIX_RESOURCE_COMMAND);
    configuredMode = state.configuredMode();
    resourceMode = state.resourceMode();
    resourceWireUsesBarrier = state.resourceWireUsesBarrier();
    resourceWireCarrierFallback = state.resourceWireCarrierFallback();
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

  private boolean isChorusUpdatesDisabled() {
    return chorusPlantUpdateStatus().disabled();
  }

  private File serverRoot() {
    File pluginsDir = getDataFolder().getParentFile();
    return pluginsDir != null ? pluginsDir.getParentFile() : null;
  }

  private void setupRegionProtection() {
    regionProtection = RegionProtection.allowAll();
    ProtectionRuntimeConfig protectionConfig = ProtectionRuntimeConfig.fromConfig(getConfig());
    if (!protectionConfig.enabled()) {
      ExortLog.info(ProtectionStartupLog.disabledByConfig());
      return;
    }
    boolean failClosed = protectionConfig.failClosedOnError();
    ProtectionRuntimeConfig.Adapters enabledAdapters = protectionConfig.adapters();
    List<CompositeRegionProtection.Adapter> adapters = new ArrayList<>();
    Set<String> missingPlugins = new LinkedHashSet<>();

    if (enabledAdapters.worldGuard()
        && !addProtectionAdapter(
            adapters,
            missingPlugins,
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
            "Towny",
            () -> createProtectionAdapter("com.zxcmc.exort.integration.protection.TownyProtection"),
            failClosed)) {
      return;
    }
    if (enabledAdapters.lands()
        && !addProtectionAdapter(
            adapters,
            missingPlugins,
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
            "Residence",
            () ->
                createProtectionAdapter(
                    "com.zxcmc.exort.integration.protection.ResidenceProtection"),
            failClosed)) {
      return;
    }

    if (adapters.isEmpty()) {
      ExortLog.info(ProtectionStartupLog.noSupportedProvider());
      registerProtectionEnableHook(missingPlugins);
      return;
    }

    CompositeRegionProtection composite =
        new CompositeRegionProtection(adapters, getLogger(), failClosed);
    regionProtection = composite;
    ExortLog.success(ProtectionStartupLog.enabled(composite.adapterNames()));
    registerProtectionEnableHook(missingPlugins);
  }

  private boolean addProtectionAdapter(
      List<CompositeRegionProtection.Adapter> adapters,
      Set<String> missingPlugins,
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
      getLogger()
          .log(
              Level.WARNING,
              pluginName
                  + " is enabled, but Exort could not initialize its protection adapter; "
                  + (failClosed ? "denying Exort actions." : "allowing Exort actions."),
              error);
      if (failClosed) {
        regionProtection = RegionProtection.denyAll();
        return false;
      }
      return true;
    }
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

  private void registerProtectionEnableHook(Set<String> pluginNames) {
    if (pluginNames.isEmpty()) {
      return;
    }
    Set<String> watchedPluginNames = Set.copyOf(pluginNames);
    Bukkit.getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onPluginEnable(PluginEnableEvent event) {
                if (!watchedPluginNames.contains(event.getPlugin().getName())) {
                  return;
                }
                setupRegionProtection();
                HandlerList.unregisterAll(this);
              }
            },
            this);
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
        customItems,
        keys,
        storageManager,
        database,
        sessionManager,
        wirelessService,
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
        () -> StorageRuntimeConfig.fromConfig(getConfig()).cacheIdleUnloadSeconds(),
        () -> wireLimit,
        () -> wireHardCap,
        () -> wireMaterial,
        () -> storageCarrier);
  }

  @Override
  public NetworkGraphCache getNetworkGraphCache() {
    return networkGraphCache;
  }

  private void reloadDefaultSortMode() {
    defaultSortModeName = StorageRuntimeConfig.fromConfig(getConfig()).defaultSortModeName();
  }

  private void ensureStorageTiersFile() {
    ConfigUpdater.update(this, "storage-tiers.yml");
  }

  private void ensureRecipesFile() {
    File file = new File(getDataFolder(), "recipes.yml");
    if (file.exists()) return;
    saveResource("recipes.yml", false);
  }
}
