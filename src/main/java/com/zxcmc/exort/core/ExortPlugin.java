package com.zxcmc.exort.core;

import com.zxcmc.exort.api.ExortApi;
import com.zxcmc.exort.api.model.StorageTierDescriptor;
import com.zxcmc.exort.block.listener.BlockListener;
import com.zxcmc.exort.block.listener.BlockListenerDependencies;
import com.zxcmc.exort.block.listener.ItemPlaceBridgeDependencies;
import com.zxcmc.exort.block.listener.ItemPlaceBridgeListener;
import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BlockBreakHandlerDependencies;
import com.zxcmc.exort.breaking.BreakAnimationSender;
import com.zxcmc.exort.breaking.BreakConfig;
import com.zxcmc.exort.breaking.BreakParticleSender;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.breaking.BreakVisualConfig;
import com.zxcmc.exort.breaking.CompositeBreakAnimationSender;
import com.zxcmc.exort.breaking.CustomBlockBreaker;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionDependencies;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.bus.engine.BusEngineDependencies;
import com.zxcmc.exort.bus.listener.BusListener;
import com.zxcmc.exort.command.ExortBrigadier;
import com.zxcmc.exort.command.ExortBrigadierDependencies;
import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.debug.LoadTestService;
import com.zxcmc.exort.debug.PickDebugService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.BusDisplayManager;
import com.zxcmc.exort.display.DisplayBreakAnimationSender;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.MonitorDisplayManager;
import com.zxcmc.exort.display.StorageDisplayManager;
import com.zxcmc.exort.display.TerminalDisplayManager;
import com.zxcmc.exort.display.WireDisplayManager;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.CreativeTabOrder;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import com.zxcmc.exort.gui.SearchDialogService;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.gui.SessionManagerDependencies;
import com.zxcmc.exort.gui.SortEvent;
import com.zxcmc.exort.gui.SortMode;
import com.zxcmc.exort.gui.listener.InventoryEvents;
import com.zxcmc.exort.gui.listener.SearchDialogListener;
import com.zxcmc.exort.gui.listener.TerminalListener;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.config.ConfigUpdater;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.metrics.ExortMetrics;
import com.zxcmc.exort.infra.resourcepack.ResourcePackService;
import com.zxcmc.exort.infra.update.UpdateChecker;
import com.zxcmc.exort.integration.protection.DebugRegionProtection;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.protection.WorldGuardProtection;
import com.zxcmc.exort.integration.protection.WorldGuardProtectionConfig;
import com.zxcmc.exort.integration.protocol.ProtocolLibCompatibility;
import com.zxcmc.exort.integration.protocol.ProtocolLibEnhancements;
import com.zxcmc.exort.integration.worldedit.WorldEditIntegration;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.items.listener.InventoryRefreshListener;
import com.zxcmc.exort.items.listener.PickListener;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.monitor.listener.MonitorListener;
import com.zxcmc.exort.monitor.listener.MonitorListenerDependencies;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.NetworkGraphCacheProvider;
import com.zxcmc.exort.placement.ExortBlockTargetResolver;
import com.zxcmc.exort.placement.FailoverPlacementGuardBackend;
import com.zxcmc.exort.placement.PaperEntityPlacementGuardBackend;
import com.zxcmc.exort.placement.PlacementGuardBackend;
import com.zxcmc.exort.placement.PlacementGuardConfig;
import com.zxcmc.exort.placement.ProtocolLibPlacementGuardBackend;
import com.zxcmc.exort.placement.RightClickPlacementGuard;
import com.zxcmc.exort.platform.ModePolicy;
import com.zxcmc.exort.platform.PaperChorusPlantUpdates;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.recipes.CraftingRulesConfig;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.recipes.listener.CraftBlockerListener;
import com.zxcmc.exort.runtime.RuntimeDisplayConfig;
import com.zxcmc.exort.runtime.RuntimeDisplayModelConfig;
import com.zxcmc.exort.runtime.RuntimeHologramConfig;
import com.zxcmc.exort.runtime.RuntimeItemModelConfig;
import com.zxcmc.exort.runtime.RuntimeMonitorScreenConfig;
import com.zxcmc.exort.runtime.RuntimeNetworkConfig;
import com.zxcmc.exort.sanity.ChunkSanityService;
import com.zxcmc.exort.sanity.DisplayCleanupService;
import com.zxcmc.exort.sanity.MarkerSanityDependencies;
import com.zxcmc.exort.sanity.MarkerSanityService;
import com.zxcmc.exort.sanity.listener.ChunkSanityListener;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageRuntimeConfig;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.listener.StorageListener;
import com.zxcmc.exort.text.ExortText;
import com.zxcmc.exort.wire.listener.WireListener;
import com.zxcmc.exort.wire.listener.WireListenerDependencies;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.listener.WirelessCraftListener;
import com.zxcmc.exort.wireless.listener.WirelessListener;
import com.zxcmc.exort.wireless.listener.WirelessListenerDependencies;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ExortPlugin extends JavaPlugin implements ExortApi, NetworkGraphCacheProvider {
  static final String MODE_FIX_RESOURCE_COMMAND = "/exort mode fix RESOURCE";
  private static final String CHORUS_FIX_COMMAND_BEFORE = "To fix this automatically, run ";
  private static final String CHORUS_FIX_COMMAND_AFTER =
      ". This command will update the Paper option, set Exort mode to RESOURCE, notify players,"
          + " and restart the server after 10 seconds.";
  private static final String VANILLA_NAMESPACE = "minecraft";

  private static final int MIN_MC_MAJOR = 1;
  private static final int MIN_MC_MINOR = 21;
  private static final int MIN_MC_PATCH = 7;
  private Database database;
  private StorageManager storageManager;
  private SessionManager sessionManager;
  private StorageKeys keys;
  private Lang lang;
  private PlayerFeedback playerFeedback;
  private ItemNameService itemNameService;
  private CustomItems customItems;
  private WirelessTerminalService wirelessService;
  private BossBarManager bossBarManager;
  private SearchDialogService searchDialogService;
  private int flushTaskId = -1;
  private int cacheEvictTaskId = -1;
  private int wireLimit;
  private int wireHardCap;
  private Material wireMaterial;
  private Material storageCarrier;
  private Material terminalCarrier;
  private Material monitorCarrier;
  private Material busCarrier;
  private long storagePeekTicks;
  private long wirePeekTicks;
  private ItemHologramManager hologramManager;
  private boolean dialogSupported;
  private WireDisplayManager wireDisplayManager;
  private StorageDisplayManager storageDisplayManager;
  private TerminalDisplayManager terminalDisplayManager;
  private MonitorDisplayManager monitorDisplayManager;
  private BusDisplayManager busDisplayManager;
  private DisplayRefreshService displayRefreshService;
  private BusService busService;
  private BusSessionManager busSessionManager;
  private BlockBreakHandler breakHandler;
  private CustomBlockBreaker customBlockBreaker;
  private BreakSoundConfig breakSoundConfig;
  private CraftingRules craftingRules;
  private RecipeService recipeService;
  private LoadTestService loadTestService;
  private CacheDebugService cacheDebugService;
  private PickDebugService pickDebugService;
  private WorldEditDebugService worldEditDebugService;
  private Metrics metrics;
  private WorldEditIntegration worldEditIntegration;
  private ResourcePackService resourcePackService;
  private ProtocolLibEnhancements protocolLibEnhancements;
  private RightClickPlacementGuard placementGuard;
  private final AtomicInteger inventoryRefreshEpoch = new AtomicInteger();
  private NetworkGraphCache networkGraphCache;
  private boolean resourceMode;
  private String configuredMode = "RESOURCE";
  private String modeFallbackReason = "";
  private volatile String defaultSortModeName = SortMode.AMOUNT.name();
  private boolean sanityScanScheduled;
  private RegionProtection regionProtection = RegionProtection.allowAll();
  private final Map<MonitorPos, Integer> recentMonitorPlacements = new ConcurrentHashMap<>();

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
    resourcePackService =
        new ResourcePackService(this, this::isResourceMode, lang::tr, ExortText::configRichText);
    Bukkit.getPluginManager().registerEvents(resourcePackService, this);
    resourcePackService.reload();
    playerFeedback = new PlayerFeedback(lang);
    itemNameService = new ItemNameService(this);
    searchDialogService = new SearchDialogService(lang);
    keys = new StorageKeys(this);
    networkGraphCache = new NetworkGraphCache(this);
    database = new Database(getLogger(), this::getDefaultSortModeName);
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
            this::getCacheDebugService,
            this::getDefaultSortModeName,
            database::setStorageSortMode,
            cache -> cache.refreshCustomItems(customItems, wirelessService, true));
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
                this::isResourceMode,
                this::isDialogSupported,
                this::getWireLimit,
                this::getWireHardCap,
                this::getWireMaterial,
                this::getStorageCarrier,
                this::getTerminalCarrier,
                () -> GuiRuntimeConfig.fromConfig(getConfig()),
                () -> GuiOverlayConfig.fromConfig(getConfig())));
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
    cancelRuntimeTasks();
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
    if (resourcePackService != null) {
      resourcePackService.stop();
      resourcePackService = null;
    }
    if (recipeService != null) {
      recipeService.unregisterAll();
    }
    if (hologramManager != null) {
      hologramManager.stop();
    }
    if (monitorDisplayManager != null) {
      monitorDisplayManager.stop();
    }
    busSessionManager = null;
    if (busService != null) {
      busService.stop();
    }
    DisplayBreakAnimationSender.clearStaleOverlays();
    if (database != null) {
      database.close();
    }
  }

  private void cancelRuntimeTasks() {
    if (flushTaskId != -1) {
      Bukkit.getScheduler().cancelTask(flushTaskId);
      flushTaskId = -1;
    }
    if (cacheEvictTaskId != -1) {
      Bukkit.getScheduler().cancelTask(cacheEvictTaskId);
      cacheEvictTaskId = -1;
    }
  }

  public StorageManager getStorageManager() {
    return storageManager;
  }

  public Database getDatabase() {
    return database;
  }

  public SessionManager getSessionManager() {
    return sessionManager;
  }

  public StorageKeys getKeys() {
    return keys;
  }

  public Lang getLang() {
    return lang;
  }

  public PlayerFeedback getPlayerFeedback() {
    return playerFeedback;
  }

  public CustomItems getCustomItems() {
    return customItems;
  }

  public WirelessTerminalService getWirelessService() {
    return wirelessService;
  }

  public BossBarManager getBossBarManager() {
    return bossBarManager;
  }

  public LoadTestService getLoadTestService() {
    return loadTestService;
  }

  public CacheDebugService getCacheDebugService() {
    return cacheDebugService;
  }

  public WorldEditDebugService getWorldEditDebugService() {
    return worldEditDebugService;
  }

  public PickDebugService getPickDebugService() {
    return pickDebugService;
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

  public int getInventoryRefreshEpoch() {
    return inventoryRefreshEpoch.get();
  }

  public void bumpInventoryRefreshEpoch() {
    inventoryRefreshEpoch.incrementAndGet();
  }

  public void refreshPlayerInventory(Player player) {
    if (player == null || customItems == null) return;
    refreshInventory(player.getInventory(), false);
    refreshInventory(player.getEnderChest(), false);
  }

  public void refreshContainerInventory(Inventory inventory) {
    refreshInventory(inventory, false);
  }

  public ItemNameService getItemNameService() {
    return itemNameService;
  }

  public SearchDialogService getSearchDialogService() {
    return searchDialogService;
  }

  public ResourcePackService getResourcePackService() {
    return resourcePackService;
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

  public void markMonitorPlaced(Block block) {
    if (block == null) return;
    int expiresAt = Bukkit.getCurrentTick() + 2;
    recentMonitorPlacements.put(MonitorPos.of(block), expiresAt);
  }

  public boolean isMonitorRecentlyPlaced(Block block) {
    if (block == null) return false;
    MonitorPos pos = MonitorPos.of(block);
    Integer expiresAt = recentMonitorPlacements.get(pos);
    if (expiresAt == null) {
      return false;
    }
    if (Bukkit.getCurrentTick() <= expiresAt) {
      return true;
    }
    recentMonitorPlacements.remove(pos);
    return false;
  }

  private record MonitorPos(UUID world, int x, int y, int z) {
    static MonitorPos of(Block block) {
      return new MonitorPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  public boolean isResourceMode() {
    return resourceMode;
  }

  private boolean ensureMinMinecraftVersion() {
    String version = Bukkit.getMinecraftVersion();
    int[] parsed = parseVersion(version);
    if (parsed == null) {
      ExortLog.warn("Unable to parse Minecraft version '" + version + "'. Proceeding anyway.");
      return true;
    }
    if (compareVersions(parsed[0], parsed[1], parsed[2], MIN_MC_MAJOR, MIN_MC_MINOR, MIN_MC_PATCH)
        < 0) {
      ExortLog.error(
          "Minecraft "
              + MIN_MC_MAJOR
              + "."
              + MIN_MC_MINOR
              + "."
              + MIN_MC_PATCH
              + "+ is required. Current: "
              + version);
      return false;
    }
    return true;
  }

  private int[] parseVersion(String version) {
    if (version == null) {
      return null;
    }
    String[] parts = version.split("\\.");
    if (parts.length < 2) {
      return null;
    }
    try {
      int major = Integer.parseInt(parts[0]);
      int minor = Integer.parseInt(parts[1]);
      int patch = parts.length > 2 ? Integer.parseInt(parts[2].replaceAll("[^0-9].*$", "")) : 0;
      return new int[] {major, minor, patch};
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private int compareVersions(
      int aMajor, int aMinor, int aPatch, int bMajor, int bMinor, int bPatch) {
    if (aMajor != bMajor) {
      return Integer.compare(aMajor, bMajor);
    }
    if (aMinor != bMinor) {
      return Integer.compare(aMinor, bMinor);
    }
    return Integer.compare(aPatch, bPatch);
  }

  public CraftingRules getCraftingRules() {
    return craftingRules;
  }

  public CompletableFuture<ItemNameService.Status> reloadRuntime() {
    ConfigUpdater.update(this, "config.yml");
    reloadConfig();
    closeRuntimeSessions();
    ensureStorageTiersFile();
    ensureRecipesFile();
    evaluateModePolicy();
    reloadResourcePackService();
    return registerRuntime(false);
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

  private void stopProtocolEnhancements() {
    if (protocolLibEnhancements == null) {
      return;
    }
    protocolLibEnhancements.unregister();
    protocolLibEnhancements = null;
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
    stopWorldEditIntegration();
    stopPlacementGuard();
    stopProtocolEnhancements();
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
  }

  private void resetReloadableDisplayState() {
    if (hologramManager != null) {
      hologramManager.stop();
    }
    if (monitorDisplayManager != null) {
      monitorDisplayManager.stop();
    }
    if (busService != null) {
      busService.stop();
    }
    wireDisplayManager = null;
    storageDisplayManager = null;
    terminalDisplayManager = null;
    monitorDisplayManager = null;
    busDisplayManager = null;
    sanityScanScheduled = false;
  }

  private RuntimeItemModelConfig configureRuntimeItemModels() {
    RuntimeItemModelConfig itemModels =
        RuntimeItemModelConfig.fromConfig(getConfig(), resourceMode);
    wireMaterial = itemModels.wireMaterial();
    storageCarrier = itemModels.storageCarrier();
    terminalCarrier = itemModels.terminalCarrier();
    monitorCarrier = itemModels.monitorCarrier();
    busCarrier = itemModels.busCarrier();
    return itemModels;
  }

  private CompletableFuture<ItemNameService.Status> registerRuntime(
      boolean refreshItemDictionaries) {
    reloadDefaultSortMode();
    String langCode = getConfig().getString("language", "en_us");
    String normalized = itemNameService.normalizeLanguage(langCode);
    if (lang == null) {
      lang = new Lang(this);
    }
    lang.reload(normalized);
    if (searchDialogService != null) {
      searchDialogService.invalidate();
    }
    CompletableFuture<ItemNameService.Status> langFuture =
        refreshItemDictionaries
            ? itemNameService.refresh(normalized)
            : itemNameService.reload(normalized);
    langFuture.thenAccept(
        status -> {
          if (!status.activeLanguage().equalsIgnoreCase(normalized)) {
            lang.reload(status.activeLanguage());
          }
        });
    CreativeTabOrder.init(this);

    RuntimeItemModelConfig itemModels = configureRuntimeItemModels();
    YamlConfiguration tiersConfig = loadStorageTiersConfig();
    StorageTier.loadFromConfig(tiersConfig.getConfigurationSection("tiers"), getLogger());
    RuntimeNetworkConfig networkConfig = RuntimeNetworkConfig.fromConfig(getConfig());
    storagePeekTicks = networkConfig.storagePeekTicks();
    wirePeekTicks = networkConfig.wirePeekTicks();
    var hologramConfig = RuntimeHologramConfig.fromConfig(getConfig(), resourceMode);
    customItems =
        new CustomItems(
            keys,
            lang,
            itemModels.wireItemModel(),
            itemModels.storageItemModel(),
            itemModels.terminalItemModel(),
            itemModels.craftingTerminalItemModel(),
            itemModels.monitorItemModel(),
            itemModels.importBusItemModel(),
            itemModels.exportBusItemModel(),
            itemModels.wirelessItemModel(),
            itemModels.wirelessDisabledModel(),
            VANILLA_NAMESPACE + ":target");
    WirelessRuntimeConfig wirelessConfig = WirelessRuntimeConfig.fromConfig(getConfig());
    wirelessService =
        new WirelessTerminalService(
            lang, keys, customItems, wirelessConfig.enabled(), wirelessConfig.rangeChunks());
    if (sessionManager != null) {
      sessionManager.reconfigure();
    }
    wireLimit = networkConfig.wireLimit();
    wireHardCap = networkConfig.wireHardCap();
    if (networkConfig.wireHardCapAdjusted()) {
      ExortLog.warn("wireHardCap is below wireLimit; value will be adjusted to " + wireLimit);
    }
    if (networkGraphCache != null) {
      networkGraphCache.invalidateAll();
    }

    stopReloadableRuntime();
    protocolLibEnhancements = ProtocolLibEnhancements.tryCreate(this, this::recordPickDebugFull);
    BreakAnimationSender breakAnimationSender =
        createBreakAnimationSender(itemModels.displayNamespace());
    unregisterReloadableRuntimeListeners();
    setupRegionProtection();
    resetReloadableDisplayState();
    hologramManager =
        new ItemHologramManager(
            this,
            keys,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            terminalCarrier,
            hologramConfig.terminal(),
            hologramConfig.storage());
    hologramManager.start();
    Bukkit.getPluginManager().registerEvents(hologramManager, this);

    RuntimeDisplayConfig wireDisplay =
        RuntimeDisplayConfig.fromConfig(
            getConfig(), resourceMode, "resourceMode.wire", this::resolveMaterial);
    String ns = itemModels.displayNamespace();
    RuntimeDisplayModelConfig displayModels =
        RuntimeDisplayModelConfig.fromConfig(getConfig(), resourceMode, ns);
    String wireEntityName = lang.tr("item.wire");
    wireDisplayManager =
        new WireDisplayManager(
            this,
            true,
            wireMaterial,
            terminalCarrier,
            storageCarrier,
            monitorCarrier,
            busCarrier,
            ns,
            displayModels.wireCenter(),
            displayModels.wireConnection(),
            resourceMode,
            wireDisplay.displayBaseMaterial(),
            wireDisplay.displayScale(),
            wireDisplay.offsetX(),
            wireDisplay.offsetY(),
            wireDisplay.offsetZ(),
            wireEntityName);
    if (wireDisplayManager.isEnabled()) {
      Bukkit.getScheduler().runTask(this, wireDisplayManager::scanLoadedChunks);
    }

    // Storage display manager
    RuntimeDisplayConfig storageDisplay =
        RuntimeDisplayConfig.fromConfig(
            getConfig(), resourceMode, "resourceMode.storage", this::resolveMaterial);
    storageDisplayManager =
        new StorageDisplayManager(
            this,
            storageCarrier,
            displayModels.storage(),
            storageDisplay.displayBaseMaterial(),
            storageDisplay.displayScale(),
            storageDisplay.offsetX(),
            storageDisplay.offsetY(),
            storageDisplay.offsetZ(),
            lang.tr("item.storage_core"));
    Bukkit.getScheduler().runTask(this, storageDisplayManager::scanLoadedChunks);

    // Terminal display manager
    RuntimeDisplayConfig terminalDisplay =
        RuntimeDisplayConfig.fromConfig(
            getConfig(), resourceMode, "resourceMode.terminal", this::resolveMaterial);
    String terminalEntityName = lang.tr("item.terminal");
    String craftingTerminalEntityName = lang.tr("item.crafting_terminal");
    terminalDisplayManager =
        new TerminalDisplayManager(
            this,
            terminalCarrier,
            displayModels.terminal(),
            displayModels.terminalDisabled(),
            displayModels.craftingTerminal(),
            displayModels.craftingTerminalDisabled(),
            terminalDisplay.displayBaseMaterial(),
            terminalDisplay.displayScale(),
            terminalDisplay.offsetX(),
            terminalDisplay.offsetY(),
            terminalDisplay.offsetZ(),
            terminalEntityName,
            craftingTerminalEntityName,
            keys,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            resourceMode);
    Bukkit.getScheduler().runTask(this, terminalDisplayManager::scanLoadedChunks);

    // Monitor display manager
    RuntimeDisplayConfig monitorDisplay =
        RuntimeDisplayConfig.fromConfig(
            getConfig(), resourceMode, "resourceMode.monitor", this::resolveMaterial);
    RuntimeMonitorScreenConfig monitorScreens =
        RuntimeMonitorScreenConfig.fromConfig(getConfig(), resourceMode);
    String monitorName = lang.tr("item.monitor");
    monitorDisplayManager =
        new MonitorDisplayManager(
            this,
            keys,
            storageManager,
            monitorCarrier,
            displayModels.monitor(),
            displayModels.monitorDisabled(),
            monitorDisplay.displayBaseMaterial(),
            monitorDisplay.displayScale(),
            monitorDisplay.offsetX(),
            monitorDisplay.offsetY(),
            monitorDisplay.offsetZ(),
            monitorName,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            monitorScreens.item(),
            monitorScreens.block(),
            monitorScreens.thinBlock(),
            monitorScreens.horizontalBlock(),
            monitorScreens.fullBlock(),
            monitorScreens.text(),
            monitorScreens.textEmpty(),
            monitorScreens.textBackgroundAlpha());
    Bukkit.getScheduler().runTask(this, monitorDisplayManager::start);

    // Bus display manager
    RuntimeDisplayConfig busDisplay =
        RuntimeDisplayConfig.fromConfig(
            getConfig(), resourceMode, "resourceMode.bus", this::resolveMaterial);
    String importBusName = lang.tr("item.import_bus");
    String exportBusName = lang.tr("item.export_bus");
    busDisplayManager =
        new BusDisplayManager(
            this,
            busCarrier,
            displayModels.importBus(),
            displayModels.exportBus(),
            busDisplay.displayBaseMaterial(),
            busDisplay.displayScale(),
            busDisplay.offsetX(),
            busDisplay.offsetY(),
            busDisplay.offsetZ(),
            importBusName,
            exportBusName);
    Bukkit.getScheduler().runTask(this, busDisplayManager::scanLoadedChunks);

    displayRefreshService =
        new DisplayRefreshService(
            this,
            wireHardCap,
            wireMaterial,
            terminalCarrier,
            monitorCarrier,
            busCarrier,
            storageCarrier,
            wireDisplayManager,
            storageDisplayManager,
            terminalDisplayManager,
            monitorDisplayManager,
            busDisplayManager);
    var chunkSanityService =
        new ChunkSanityService(
            this,
            new DisplayCleanupService(
                this, wireMaterial, storageCarrier, terminalCarrier, monitorCarrier, busCarrier),
            new MarkerSanityService(
                new MarkerSanityDependencies(
                    this,
                    displayRefreshService,
                    this::getHologramManager,
                    this::getBusService,
                    storageManager,
                    database,
                    wireMaterial,
                    storageCarrier,
                    terminalCarrier,
                    monitorCarrier,
                    busCarrier)),
            displayRefreshService,
            this::getWorldEditDebugService,
            () -> {
              if (networkGraphCache != null) {
                networkGraphCache.invalidateAll();
              }
            });
    // Unified sanity listener (handles carrier changes and display refresh)
    var chunkSanityListener =
        new ChunkSanityListener(
            this,
            chunkSanityService,
            () -> {
              if (networkGraphCache != null) {
                networkGraphCache.invalidateAll();
              }
            });
    Bukkit.getPluginManager().registerEvents(chunkSanityListener, this);

    // Schedule sanity scan for loaded chunks to update carriers/displays if materials changed
    if (!sanityScanScheduled) {
      sanityScanScheduled = true;
      Bukkit.getScheduler().runTask(this, chunkSanityService::scanLoadedChunks);
    }

    BusRuntimeConfig busRuntime = BusRuntimeConfig.fromConfig(getConfig());
    var busDependencies =
        new BusEngineDependencies(
            this,
            keys,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            storageId -> sessionManager.renderStorage(storageId, SortEvent.NONE));
    busService =
        new BusService(
            busDependencies, storageManager, database, busCarrier, busRuntime, wirelessService);
    busService.start();
    Bukkit.getScheduler().runTask(this, busService::scanLoadedChunks);
    var busSessionDependencies =
        new BusSessionDependencies(
            this,
            keys,
            bossBarManager,
            this::isResourceMode,
            this::getBusCarrier,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            () -> GuiRuntimeConfig.fromConfig(getConfig()),
            () -> GuiOverlayConfig.fromConfig(getConfig()));
    busSessionManager = new BusSessionManager(busSessionDependencies, busService, lang);
    busSessionManager.reconfigure();

    breakHandler =
        new BlockBreakHandler(
            new BlockBreakHandlerDependencies(
                this,
                customItems,
                wireMaterial,
                storageCarrier,
                terminalCarrier,
                monitorCarrier,
                busCarrier,
                hologramManager,
                wireDisplayManager,
                displayRefreshService,
                breakAnimationSender,
                storageManager,
                sessionManager,
                this::getMonitorDisplayManager,
                this::getBusSessionManager,
                this::getBusService,
                this::getNetworkGraphCache,
                regionProtection,
                playerFeedback));
    var breakConfig = BreakConfig.fromConfig(getConfig(), getLogger());
    breakSoundConfig = BreakSoundConfig.fromConfig(getConfig());
    customBlockBreaker =
        new CustomBlockBreaker(
            this,
            regionProtection,
            breakHandler,
            breakConfig,
            breakSoundConfig,
            breakAnimationSender,
            wireMaterial,
            storageCarrier,
            terminalCarrier,
            monitorCarrier,
            busCarrier);
    registerRuntimeListeners();
    // Ensure terminals/monitors refresh after mode switch so displays don't stay disabled until
    // interaction.
    Bukkit.getScheduler()
        .runTaskLater(
            this,
            () -> {
              if (networkGraphCache != null) {
                networkGraphCache.invalidateAll();
              }
              for (var world : Bukkit.getWorlds()) {
                for (var chunk : world.getLoadedChunks()) {
                  displayRefreshService.refreshChunk(chunk);
                  if (!ChunkMarkerStore.hasAnyBlockData(this, chunk)) continue;
                  ChunkMarkerStore.forEachBlock(
                      this,
                      chunk,
                      (block, root) -> {
                        if (StorageMarker.get(this, block).isPresent()) {
                          displayRefreshService.refreshNetworkFrom(block);
                        }
                      });
                }
              }
              if (storageManager != null) {
                storageManager.refreshLoadedCustomItems();
              }
              for (var player : Bukkit.getOnlinePlayers()) {
                refreshPlayerInventory(player);
              }
              bumpInventoryRefreshEpoch();
            },
            5L);
    if (worldEditIntegration == null) {
      worldEditIntegration = WorldEditIntegration.tryRegister(this);
      if (worldEditIntegration == null) {
        Bukkit.getPluginManager()
            .registerEvents(
                new Listener() {
                  @EventHandler
                  public void onPluginEnable(PluginEnableEvent event) {
                    if (worldEditIntegration != null) {
                      HandlerList.unregisterAll(this);
                      return;
                    }
                    String name = event.getPlugin().getName();
                    if (!"WorldEdit".equals(name) && !"FastAsyncWorldEdit".equals(name)) {
                      return;
                    }
                    worldEditIntegration = WorldEditIntegration.tryRegister(ExortPlugin.this);
                    if (worldEditIntegration != null) {
                      HandlerList.unregisterAll(this);
                    }
                  }
                },
                this);
      }
    }
    scheduleRuntimeTasks();
    return langFuture;
  }

  private void registerRuntimeListeners() {
    customBlockBreaker.start();
    Bukkit.getPluginManager().registerEvents(customBlockBreaker, this);
    Bukkit.getPluginManager()
        .registerEvents(
            new BlockListener(
                new BlockListenerDependencies(
                    this,
                    storageManager,
                    keys,
                    customItems,
                    wireMaterial,
                    hologramManager,
                    this::getHologramManager,
                    wireDisplayManager,
                    storageCarrier,
                    terminalCarrier,
                    monitorCarrier,
                    busCarrier,
                    breakHandler,
                    regionProtection,
                    playerFeedback,
                    this::getDisplayRefreshService,
                    this::getMonitorDisplayManager,
                    this::getBusService,
                    this::getNetworkGraphCache,
                    () -> {
                      if (sessionManager != null) {
                        sessionManager.revalidateSessions();
                      }
                    },
                    database::setStorageTier,
                    this::getBreakSoundConfig,
                    () -> BusRuntimeConfig.fromConfig(getConfig()))),
            this);
    Bukkit.getPluginManager()
        .registerEvents(
            new TerminalListener(
                this,
                regionProtection,
                playerFeedback,
                block -> {
                  if (terminalDisplayManager != null) {
                    terminalDisplayManager.refresh(block);
                  }
                },
                database::setStorageTier,
                storageManager,
                sessionManager,
                keys,
                wireLimit,
                wireHardCap,
                wireMaterial,
                storageCarrier,
                terminalCarrier),
            this);
    Bukkit.getPluginManager()
        .registerEvents(
            new BusListener(
                this,
                regionProtection,
                block -> {
                  if (busDisplayManager != null) {
                    busDisplayManager.refresh(block);
                  }
                },
                busSessionManager,
                busCarrier),
            this);
    Bukkit.getPluginManager()
        .registerEvents(new InventoryEvents(sessionManager, busSessionManager), this);
    dialogSupported = detectDialogSupport();
    Bukkit.getPluginManager()
        .registerEvents(new SearchDialogListener(sessionManager, searchDialogService, this), this);
    Bukkit.getPluginManager()
        .registerEvents(
            new StorageListener(
                this, regionProtection, bossBarManager, storagePeekTicks, storageCarrier),
            this);
    Bukkit.getPluginManager()
        .registerEvents(
            new WireListener(
                new WireListenerDependencies(
                    this,
                    regionProtection,
                    bossBarManager,
                    keys,
                    wireLimit,
                    wireHardCap,
                    wireMaterial,
                    wirePeekTicks,
                    storageCarrier)),
            this);
    var pickListener =
        new PickListener(
            this,
            customItems,
            keys,
            this::recordPickDebug,
            wireMaterial,
            storageCarrier,
            terminalCarrier,
            monitorCarrier,
            busCarrier);
    Bukkit.getPluginManager().registerEvents(pickListener, this);
    if (protocolLibEnhancements != null) {
      protocolLibEnhancements.registerPickBridge(pickListener);
    }
    Bukkit.getPluginManager()
        .registerEvents(
            new ItemPlaceBridgeListener(
                new ItemPlaceBridgeDependencies(
                    this,
                    storageManager,
                    customItems,
                    keys,
                    wireMaterial,
                    storageCarrier,
                    terminalCarrier,
                    monitorCarrier,
                    busCarrier,
                    regionProtection,
                    playerFeedback,
                    this::getDisplayRefreshService,
                    this::getHologramManager,
                    this::getMonitorDisplayManager,
                    this::getBusService,
                    this::getNetworkGraphCache,
                    () -> {
                      if (sessionManager != null) {
                        sessionManager.revalidateSessions();
                      }
                    },
                    this::markMonitorPlaced,
                    database::setStorageTier,
                    this::getBreakSoundConfig,
                    () -> BusRuntimeConfig.fromConfig(getConfig()))),
            this);
    Bukkit.getPluginManager()
        .registerEvents(
            new MonitorListener(
                new MonitorListenerDependencies(
                    this,
                    regionProtection,
                    keys,
                    bossBarManager,
                    itemNameService,
                    monitorCarrier,
                    wireMaterial,
                    storageCarrier,
                    this::getWireLimit,
                    this::getWireHardCap,
                    () -> storagePeekTicks,
                    this::isMonitorRecentlyPlaced,
                    block -> {
                      if (monitorDisplayManager != null) {
                        monitorDisplayManager.refresh(block);
                      }
                    })),
            this);
    PlacementGuardConfig placementConfig = PlacementGuardConfig.fromConfig(getConfig());
    if (placementConfig.enabled()) {
      PlacementGuardBackend placementGuardBackend = createPlacementGuardBackend(placementConfig);
      placementGuard =
          new RightClickPlacementGuard(
              this,
              customItems,
              customBlockBreaker,
              new ExortBlockTargetResolver(
                  this, wireMaterial, storageCarrier, terminalCarrier, monitorCarrier, busCarrier),
              placementGuardBackend,
              placementConfig.pollIntervalTicks(),
              placementConfig.targetRangeBlocks(),
              placementConfig.guardScale(),
              placementConfig.cornerInset());
      Bukkit.getPluginManager().registerEvents(placementGuard, this);
      placementGuard.start();
    } else if (protocolLibEnhancements != null) {
      protocolLibEnhancements.markPlacementGuardDisabledByConfig();
    }
    Bukkit.getPluginManager()
        .registerEvents(
            new InventoryRefreshListener(
                this,
                this::getInventoryRefreshEpoch,
                this::refreshPlayerInventory,
                this::refreshContainerInventory),
            this);
    CraftingRulesConfig craftingConfig = CraftingRulesConfig.fromConfig(getConfig());
    if (recipeService != null) {
      recipeService.unregisterAll();
    }
    craftingRules =
        new CraftingRules(keys, craftingConfig.blockVanilla(), craftingConfig.allowExternal());
    Bukkit.getPluginManager().registerEvents(new CraftBlockerListener(craftingRules), this);
    recipeService = new RecipeService(this, customItems, wirelessService);
    recipeService.reload();
    Bukkit.getPluginManager()
        .registerEvents(
            new WirelessListener(
                new WirelessListenerDependencies(
                    this,
                    wirelessService,
                    storageManager,
                    customItems,
                    regionProtection,
                    bossBarManager,
                    playerFeedback,
                    database,
                    sessionManager,
                    keys,
                    wireLimit,
                    wireHardCap,
                    wireMaterial,
                    storageCarrier)),
            this);
    Bukkit.getPluginManager().registerEvents(new WirelessCraftListener(wirelessService), this);
  }

  private PlacementGuardBackend createPlacementGuardBackend(PlacementGuardConfig config) {
    PaperEntityPlacementGuardBackend paperBackend =
        new PaperEntityPlacementGuardBackend(this, config.guardScale());
    if (!config.protocolLibGuardEnabled()) {
      if (protocolLibEnhancements != null) {
        protocolLibEnhancements.markPlacementGuardDisabledByConfig();
      }
      return paperBackend;
    }
    if (protocolLibEnhancements != null) {
      var packets = protocolLibEnhancements.tryCreatePlacementGuardPackets(config.guardScale());
      if (packets != null) {
        final FailoverPlacementGuardBackend[] holder = new FailoverPlacementGuardBackend[1];
        ProtocolLibPlacementGuardBackend protocolBackend =
            new ProtocolLibPlacementGuardBackend(
                packets,
                reason -> {
                  protocolLibEnhancements.markPlacementGuardRuntimeFallback(reason);
                  holder[0].switchToPaperFallback(reason);
                });
        FailoverPlacementGuardBackend failoverBackend =
            new FailoverPlacementGuardBackend(protocolBackend, paperBackend);
        holder[0] = failoverBackend;
        return failoverBackend;
      }
    }
    if (protocolLibEnhancements == null) {
      ExortLog.warn(protocolLibPlacementGuardUnavailableMessage());
    }
    return paperBackend;
  }

  private String protocolLibPlacementGuardUnavailableMessage() {
    var protocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib");
    if (protocolLib == null) {
      return "[ProtocolLib] Placement guard is enabled but ProtocolLib is not installed; using"
          + " Paper entity placement guard.";
    }
    String version = protocolLib.getPluginMeta().getVersion();
    return "[ProtocolLib] Placement guard is enabled but ProtocolLib "
        + version
        + " is unavailable; using Paper entity placement guard. "
        + ProtocolLibCompatibility.failureAdvice(Bukkit.getMinecraftVersion(), version);
  }

  public long getStoragePeekTicks() {
    return storagePeekTicks;
  }

  public RegionProtection getRegionProtection() {
    return regionProtection;
  }

  private void evaluateModePolicy() {
    String rawMode = getConfig().getString("mode", ModePolicy.DEFAULT_MODE);
    ModePolicy policy = ModePolicy.evaluate(rawMode, isChorusUpdatesDisabled());
    if (policy.unknownMode()) {
      ExortLog.warn("Unknown mode '" + rawMode + "' in config.yml; using RESOURCE.");
    }
    configuredMode = policy.configuredMode();
    resourceMode = policy.resourceMode();
    modeFallbackReason = policy.fallbackReason();
    if (modeFallbackReason.isBlank()) return;
    ExortLog.warn(modeFallbackReason);
    ExortLog.warn(chorusFallbackHelpLines().get(0));
    ExortLog.warnCommand(
        CHORUS_FIX_COMMAND_BEFORE, MODE_FIX_RESOURCE_COMMAND, CHORUS_FIX_COMMAND_AFTER);
    ExortLog.warn(chorusFallbackHelpLines().get(2));
  }

  static List<String> chorusFallbackHelpLines() {
    return List.of(
        "It is HIGHLY recommended to enable this setting for improved performance and prevent bugs"
            + " with chorus-plants which are used to display wires by default in RESOURCE mode.",
        CHORUS_FIX_COMMAND_BEFORE + MODE_FIX_RESOURCE_COMMAND + CHORUS_FIX_COMMAND_AFTER,
        "Until then, Exort effective mode is VANILLA and resource-pack delivery is disabled.");
  }

  public boolean canEnableResourceMode() {
    return isChorusUpdatesDisabled();
  }

  public String getConfiguredMode() {
    return configuredMode;
  }

  public String getEffectiveMode() {
    return resourceMode ? "RESOURCE" : "VANILLA";
  }

  public String getModeFallbackReason() {
    return modeFallbackReason;
  }

  public PaperChorusPlantUpdates.Status chorusPlantUpdateStatus() {
    return PaperChorusPlantUpdates.read(serverRoot());
  }

  public PaperChorusPlantUpdates.FixResult disableChorusPlantUpdatesInPaperConfig() {
    return PaperChorusPlantUpdates.disable(serverRoot());
  }

  private int refreshInventory(Inventory inventory, boolean inStorage) {
    if (inventory == null || customItems == null) return 0;
    int changed = 0;
    int size = inventory.getSize();
    for (int i = 0; i < size; i++) {
      ItemStack stack = inventory.getItem(i);
      if (stack == null || stack.getType() == Material.AIR) continue;
      if (!customItems.isCustomItem(stack)) continue;
      if (customItems.refreshItem(stack, wirelessService, inStorage)) {
        inventory.setItem(i, stack);
        changed++;
      }
    }
    return changed;
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
    WorldGuardProtectionConfig protectionConfig =
        WorldGuardProtectionConfig.fromConfig(getConfig());
    if (!protectionConfig.enabled()) {
      ExortLog.info("[WorldGuard] Integration disabled by config.");
      return;
    }
    boolean failClosed = protectionConfig.failClosedOnError();
    var wg = getServer().getPluginManager().getPlugin("WorldGuard");
    if (wg == null || !wg.isEnabled()) {
      ExortLog.info("[WorldGuard] Integration disabled: plugin not found.");
      registerWorldGuardEnableHook();
      return;
    }
    try {
      regionProtection = new WorldGuardProtection(getLogger(), failClosed);
    } catch (IllegalStateException error) {
      getLogger()
          .log(
              Level.WARNING,
              "WorldGuard is enabled, but Exort could not initialize its protection adapter; "
                  + (failClosed ? "denying Exort actions." : "allowing Exort actions."),
              error);
      regionProtection = failClosed ? RegionProtection.denyAll() : RegionProtection.allowAll();
      return;
    }
    if (protectionConfig.debug()) {
      regionProtection = new DebugRegionProtection(regionProtection, getLogger(), true);
    }
    ExortLog.success("[WorldGuard] Integration enabled.");
  }

  private void registerWorldGuardEnableHook() {
    Bukkit.getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onPluginEnable(PluginEnableEvent event) {
                if (!"WorldGuard".equals(event.getPlugin().getName())) {
                  return;
                }
                setupRegionProtection();
                HandlerList.unregisterAll(this);
              }
            },
            this);
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
        loadTestService,
        itemNameService,
        this::getResourcePackService,
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
        this::getConfiguredMode,
        this::getEffectiveMode,
        this::getModeFallbackReason,
        () -> getPluginMeta().getVersion(),
        this::chorusPlantUpdateStatus,
        this::disableChorusPlantUpdatesInPaperConfig,
        () -> StorageRuntimeConfig.fromConfig(getConfig()).cacheIdleUnloadSeconds(),
        this::getWireLimit,
        this::getWireHardCap,
        this::getWireMaterial,
        this::getStorageCarrier);
  }

  public Material getWireMaterial() {
    return wireMaterial;
  }

  public Material getStorageCarrier() {
    return storageCarrier;
  }

  public Material getTerminalCarrier() {
    return terminalCarrier;
  }

  public Material getMonitorCarrier() {
    return monitorCarrier;
  }

  public Material getBusCarrier() {
    return busCarrier;
  }

  public WireDisplayManager getWireDisplayManager() {
    return wireDisplayManager;
  }

  public StorageDisplayManager getStorageDisplayManager() {
    return storageDisplayManager;
  }

  public TerminalDisplayManager getTerminalDisplayManager() {
    return terminalDisplayManager;
  }

  public MonitorDisplayManager getMonitorDisplayManager() {
    return monitorDisplayManager;
  }

  public BusDisplayManager getBusDisplayManager() {
    return busDisplayManager;
  }

  public DisplayRefreshService getDisplayRefreshService() {
    return displayRefreshService;
  }

  public BreakSoundConfig getBreakSoundConfig() {
    return breakSoundConfig;
  }

  public BusService getBusService() {
    return busService;
  }

  public BusSessionManager getBusSessionManager() {
    return busSessionManager;
  }

  @Override
  public NetworkGraphCache getNetworkGraphCache() {
    return networkGraphCache;
  }

  public int getWireLimit() {
    return wireLimit;
  }

  public int getWireHardCap() {
    return wireHardCap;
  }

  public String getDefaultSortModeName() {
    return defaultSortModeName;
  }

  private void reloadDefaultSortMode() {
    defaultSortModeName = StorageRuntimeConfig.fromConfig(getConfig()).defaultSortModeName();
  }

  public ItemHologramManager getHologramManager() {
    return hologramManager;
  }

  public boolean isDialogSupported() {
    return dialogSupported;
  }

  private boolean detectDialogSupport() {
    try {
      Class.forName("io.papermc.paper.dialog.Dialog", false, getClass().getClassLoader());
      Class.forName(
          "io.papermc.paper.event.player.PlayerCustomClickEvent",
          false,
          getClass().getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private void ensureStorageTiersFile() {
    ConfigUpdater.update(this, "storage-tiers.yml");
  }

  private void ensureRecipesFile() {
    File file = new File(getDataFolder(), "recipes.yml");
    if (file.exists()) return;
    saveResource("recipes.yml", false);
  }

  private void scheduleRuntimeTasks() {
    scheduleFlushTask();
    scheduleCacheEviction();
  }

  private void scheduleCacheEviction() {
    if (cacheEvictTaskId != -1) {
      Bukkit.getScheduler().cancelTask(cacheEvictTaskId);
      cacheEvictTaskId = -1;
    }
    if (storageManager == null) return;
    StorageRuntimeConfig storageConfig = StorageRuntimeConfig.fromConfig(getConfig());
    long idleSeconds = storageConfig.cacheIdleUnloadSeconds();
    long checkSeconds = storageConfig.cacheIdleCheckSeconds();
    if (idleSeconds <= 0 || checkSeconds <= 0) return;
    long idleMs = idleSeconds * 1000L;
    cacheEvictTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                this,
                () -> storageManager.evictIdleCaches(idleMs),
                checkSeconds * 20L,
                checkSeconds * 20L);
  }

  private void scheduleFlushTask() {
    if (flushTaskId != -1) {
      Bukkit.getScheduler().cancelTask(flushTaskId);
      flushTaskId = -1;
    }
    if (storageManager == null) return;
    int flushSeconds = StorageRuntimeConfig.fromConfig(getConfig()).flushIntervalSeconds();
    if (flushSeconds <= 0) return;
    flushTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                this, storageManager::flushDirtyCaches, flushSeconds * 20L, flushSeconds * 20L);
  }

  private YamlConfiguration loadStorageTiersConfig() {
    File tiersFile = new File(getDataFolder(), "storage-tiers.yml");
    YamlConfiguration cfg = new YamlConfiguration();
    if (!tiersFile.exists()) {
      return cfg;
    }
    try {
      cfg.load(tiersFile);
    } catch (Exception e) {
      ExortLog.warn("Failed to load storage-tiers.yml: " + e.getMessage());
    }
    return cfg;
  }

  private Material resolveMaterial(String name, Material fallback) {
    if (name == null) {
      if (fallback != null) {
        ExortLog.warn("Invalid material 'null', falling back to " + fallback);
      } else {
        ExortLog.warn("Invalid material 'null'");
      }
      return fallback;
    }
    String raw = name.trim();
    if (raw.isEmpty()) {
      if (fallback != null) {
        ExortLog.warn("Invalid material '" + name + "', falling back to " + fallback);
      } else {
        ExortLog.warn("Invalid material '" + name + "'");
      }
      return fallback;
    }
    String id = raw;
    int colon = id.indexOf(':');
    if (colon >= 0 && colon + 1 < id.length()) {
      id = id.substring(colon + 1);
    }
    id = id.trim().toUpperCase(Locale.ROOT);
    try {
      return Material.valueOf(id);
    } catch (IllegalArgumentException e) {
      if (fallback != null) {
        ExortLog.warn("Invalid material '" + name + "', falling back to " + fallback);
      } else {
        ExortLog.warn("Invalid material '" + name + "'");
      }
      return fallback;
    }
  }

  private BreakAnimationSender createBreakAnimationSender(String resourceNamespace) {
    BreakVisualConfig visualConfig = BreakVisualConfig.fromConfig(getConfig());
    DisplayBreakAnimationSender.clearStaleOverlays();
    if (resourceMode) {
      List<BreakAnimationSender> senders = new ArrayList<>();
      BreakVisualConfig.ResourceOverlayConfig overlay = visualConfig.resourceOverlay();
      if (overlay.enabled()) {
        Material base = resolveMaterial(overlay.displayBaseMaterial(), Material.PAPER);
        senders.add(
            new DisplayBreakAnimationSender(
                this, base, resourceNamespace, overlay.modelPrefix(), overlay.displayScale()));
      }
      if (visualConfig.resourceParticles().enabled()) {
        senders.add(createResourceBreakParticleSender(visualConfig.resourceParticles()));
      }
      return CompositeBreakAnimationSender.of(senders);
    }

    if (!visualConfig.vanillaParticles().enabled()) {
      return BreakAnimationSender.NOOP;
    }
    return BreakParticleSender.vanilla(this, visualConfig.vanillaParticles().settings());
  }

  private BreakAnimationSender createResourceBreakParticleSender(
      BreakVisualConfig.ResourceParticleConfig particleConfig) {
    Material material = resolveMaterial(particleConfig.materialName(), Material.NETHERITE_BLOCK);
    if (material == null || !material.isBlock()) {
      ExortLog.warn(
          "Invalid RESOURCE break particle block material '"
              + particleConfig.materialName()
              + "', falling back to NETHERITE_BLOCK");
      material = Material.NETHERITE_BLOCK;
    }
    return BreakParticleSender.resource(this, particleConfig.settings(), material);
  }

  // materialFromModel removed: item materials are always PAPER
}
