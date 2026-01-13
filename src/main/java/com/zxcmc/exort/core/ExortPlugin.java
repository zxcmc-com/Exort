package com.zxcmc.exort.core;

import com.zxcmc.exort.api.ExortApi;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.bus.listener.BusListener;
import com.zxcmc.exort.core.breaking.BlockBreakHandler;
import com.zxcmc.exort.core.breaking.BreakConfig;
import com.zxcmc.exort.core.breaking.BreakSoundConfig;
import com.zxcmc.exort.core.breaking.CustomBlockBreaker;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.commands.ExortBrigadier;
import com.zxcmc.exort.core.config.ConfigUpdater;
import com.zxcmc.exort.core.db.Database;
import com.zxcmc.exort.core.i18n.ItemNameService;
import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.listeners.BlockListener;
import com.zxcmc.exort.core.listeners.ChunkSanityListener;
import com.zxcmc.exort.core.listeners.CraftBlockerListener;
import com.zxcmc.exort.core.listeners.InventoryEvents;
import com.zxcmc.exort.core.listeners.InventoryRefreshListener;
import com.zxcmc.exort.core.listeners.ItemPlaceBridgeListener;
import com.zxcmc.exort.core.listeners.MonitorListener;
import com.zxcmc.exort.core.listeners.PickListener;
import com.zxcmc.exort.core.listeners.SearchDialogListener;
import com.zxcmc.exort.core.listeners.StorageListener;
import com.zxcmc.exort.core.listeners.TerminalListener;
import com.zxcmc.exort.core.listeners.WireListener;
import com.zxcmc.exort.core.marker.MarkerCoords;
import com.zxcmc.exort.core.network.NetworkGraphCache;
import com.zxcmc.exort.core.protection.DebugRegionProtection;
import com.zxcmc.exort.core.protection.RegionProtection;
import com.zxcmc.exort.core.protection.WorldGuardProtection;
import com.zxcmc.exort.core.recipes.CraftingRules;
import com.zxcmc.exort.core.recipes.RecipeService;
import com.zxcmc.exort.core.resourcepack.NexoPackBridge;
import com.zxcmc.exort.core.resourcepack.PackExporter;
import com.zxcmc.exort.core.sanity.ChunkSanityService;
import com.zxcmc.exort.core.sanity.DisplayCleanupService;
import com.zxcmc.exort.core.sanity.MarkerSanityService;
import com.zxcmc.exort.core.ui.BossBarManager;
import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.debug.LoadTestService;
import com.zxcmc.exort.display.BusDisplayManager;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.MonitorDisplayManager;
import com.zxcmc.exort.display.StorageDisplayManager;
import com.zxcmc.exort.display.TerminalDisplayManager;
import com.zxcmc.exort.display.WireDisplayManager;
import com.zxcmc.exort.gui.CreativeTabOrder;
import com.zxcmc.exort.gui.SearchDialogService;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.listener.WirelessCraftListener;
import com.zxcmc.exort.wireless.listener.WirelessListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ExortPlugin extends JavaPlugin implements ExortApi {
  private static final int MIN_MC_MAJOR = 1;
  private static final int MIN_MC_MINOR = 21;
  private static final int MIN_MC_PATCH = 7;
  private Database database;
  private StorageManager storageManager;
  private SessionManager sessionManager;
  private StorageKeys keys;
  private Lang lang;
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
  private Metrics metrics;
  private final AtomicInteger inventoryRefreshEpoch = new AtomicInteger();
  private NetworkGraphCache networkGraphCache;
  private boolean resourceMode;
  private boolean sanityScanScheduled;
  private RegionProtection regionProtection = RegionProtection.allowAll();
  private final Map<MonitorPos, Integer> recentMonitorPlacements = new ConcurrentHashMap<>();

  @Override
  public void onEnable() {
    if (!ensureMinMinecraftVersion()) {
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    saveDefaultConfig();
    PackExporter.exportPack(this);
    NexoPackBridge.copyIfPresent(this);
    ConfigUpdater.update(this, "config.yml");
    reloadConfig();
    ensureStorageTiersFile();
    ensureRecipesFile();
    switchToResourceOnFirstRun();
    enforcePaperChorusSetting();
    lang = new Lang(this);
    itemNameService = new ItemNameService(this);
    searchDialogService = new SearchDialogService(lang);
    keys = new StorageKeys(this);
    networkGraphCache = new NetworkGraphCache(this);
    database = new Database(this);
    File dbFile = new File(new File(getDataFolder(), "db"), "storage.db");
    boolean freshDb = !dbFile.exists();
    try {
      database.init(dbFile);
    } catch (SQLException e) {
      getLogger().log(Level.SEVERE, "Failed to init database", e);
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    storageManager = new StorageManager(this, database);
    sessionManager = new SessionManager(this);
    bossBarManager = new BossBarManager(this, storageManager, lang);
    loadTestService = new LoadTestService(this, database, bossBarManager, lang);
    cacheDebugService = new CacheDebugService(this);
    metrics = new Metrics(this, 28841);
    registerMetricsCharts();

    registerRuntime();
    registerBrigadierCommands();
    if (freshDb && loadTestService != null) {
      Bukkit.getScheduler()
          .runTaskLater(this, () -> loadTestService.start(Bukkit.getConsoleSender(), -1, 15), 100L);
    }
  }

  @Override
  public void onDisable() {
    if (flushTaskId != -1) {
      Bukkit.getScheduler().cancelTask(flushTaskId);
    }
    if (cacheEvictTaskId != -1) {
      Bukkit.getScheduler().cancelTask(cacheEvictTaskId);
      cacheEvictTaskId = -1;
    }
    if (sessionManager != null) {
      sessionManager.allSessions().stream()
          .toList()
          .forEach(session -> session.getViewer().closeInventory());
    }
    if (busSessionManager != null) {
      for (var player : Bukkit.getOnlinePlayers()) {
        if (busSessionManager.sessionFor(player) != null) {
          player.closeInventory();
        }
      }
    }
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
    if (recipeService != null) {
      recipeService.unregisterAll();
    }
    if (hologramManager != null) {
      hologramManager.stop();
    }
    if (monitorDisplayManager != null) {
      monitorDisplayManager.stop();
    }
    if (busService != null) {
      busService.stop();
    }
    if (customBlockBreaker != null) {
      customBlockBreaker.shutdown();
      customBlockBreaker = null;
    }
    if (customBlockBreaker != null) {
      customBlockBreaker.shutdown();
    }
    if (database != null) {
      database.close();
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

  private void registerMetricsCharts() {
    if (metrics == null) return;
    metrics.addCustomChart(
        new SimplePie(
            "mode",
            () ->
                "RESOURCE".equalsIgnoreCase(getConfig().getString("mode", "VANILLA"))
                    ? "resource"
                    : "vanilla"));
    metrics.addCustomChart(
        new SimplePie(
            "language", () -> getConfig().getString("language", "en_us").toLowerCase(Locale.ROOT)));
    metrics.addCustomChart(
        new SimplePie(
            "wireless_enabled",
            () -> getConfig().getBoolean("wireless.enabled", true) ? "enabled" : "disabled"));
    metrics.addCustomChart(
        new SimplePie(
            "recipes_enabled",
            () -> getConfig().getBoolean("recipes.enabled", true) ? "enabled" : "disabled"));
    metrics.addCustomChart(
        new SimplePie(
            "worldguard_state",
            () -> {
              boolean enabled = getConfig().getBoolean("worldguard.enabled", true);
              if (!enabled) return "disabled";
              boolean present = getServer().getPluginManager().isPluginEnabled("WorldGuard");
              return present ? "active" : "missing";
            }));
    metrics.addCustomChart(
        new SimplePie(
            "bus_storage_targets",
            () ->
                getConfig().getBoolean("bus.allowStorageTargets", true) ? "enabled" : "disabled"));
    metrics.addCustomChart(
        new SimplePie(
            "default_sort_mode",
            () -> getConfig().getString("defaultSortMode", "AMOUNT").toLowerCase(Locale.ROOT)));
    metrics.addCustomChart(
        new SimplePie(
            "bus_default_import",
            () ->
                getConfig()
                    .getString("bus.defaultMode.import", "WHITELIST")
                    .toLowerCase(Locale.ROOT)));
    metrics.addCustomChart(
        new SimplePie(
            "bus_default_export",
            () ->
                getConfig()
                    .getString("bus.defaultMode.export", "WHITELIST")
                    .toLowerCase(Locale.ROOT)));
  }

  @Override
  public String getVersion() {
    return getPluginMeta().getVersion();
  }

  @Override
  public Optional<StorageTier> getStorageTier(String key) {
    return StorageTier.fromString(key);
  }

  @Override
  public Collection<StorageTier> getStorageTiers() {
    return StorageTier.allTiers();
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
      getLogger()
          .warning("Unable to parse Minecraft version '" + version + "'. Proceeding anyway.");
      return true;
    }
    if (compareVersions(parsed[0], parsed[1], parsed[2], MIN_MC_MAJOR, MIN_MC_MINOR, MIN_MC_PATCH)
        < 0) {
      getLogger()
          .severe(
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
    ensureStorageTiersFile();
    ensureRecipesFile();
    switchToResourceOnFirstRun();
    enforcePaperChorusSetting();
    return registerRuntime();
  }

  private CompletableFuture<ItemNameService.Status> registerRuntime() {
    String langCode = getConfig().getString("language", "en_us");
    String normalized = itemNameService.normalizeLanguage(langCode);
    if (lang == null) {
      lang = new Lang(this);
    }
    lang.reload(normalized);
    if (searchDialogService != null) {
      searchDialogService.invalidate();
    }
    CompletableFuture<ItemNameService.Status> langFuture = itemNameService.reload(normalized);
    langFuture.thenAccept(
        status -> {
          if (!status.activeLanguage().equalsIgnoreCase(normalized)) {
            lang.reload(status.activeLanguage());
          }
        });
    CreativeTabOrder.init(this);

    resourceMode = "RESOURCE".equalsIgnoreCase(getConfig().getString("mode", "VANILLA"));

    // Carriers are always used; vanilla forces BARRIER carriers and minecraft namespace models.
    String vanillaNs = "minecraft";
    String resourceNs = getConfig().getString("resourceMode.namespace", "exort");
    String wireItemModel;
    String storageItemModel;
    String terminalItemModel;
    String craftingTerminalItemModel;
    String monitorItemModel;
    String wirelessItemModel;
    String wirelessDisabledModel;
    String importBusItemModel;
    String exportBusItemModel;
    if (resourceMode) {
      wireMaterial = Carriers.CHORUS_MATERIAL;
      storageCarrier = Carriers.CARRIER_BARRIER;
      terminalCarrier = Carriers.CARRIER_BARRIER;
      monitorCarrier = Carriers.CARRIER_BARRIER;
      busCarrier = Carriers.CARRIER_BARRIER;
      wireItemModel =
          normalizeModelId(
              getConfig().getString("resourceMode.wire.itemModel", "wire/center"), resourceNs);
      storageItemModel =
          normalizeModelId(
              getConfig().getString("resourceMode.storage.itemModel", "storage/storage"),
              resourceNs);
      terminalItemModel =
          normalizeModelId(
              getConfig().getString("resourceMode.terminal.itemModel", "terminal/inventory"),
              resourceNs);
      craftingTerminalItemModel =
          normalizeModelId(
              getConfig()
                  .getString("resourceMode.craftingTerminal.itemModel", "terminal/workbench"),
              resourceNs);
      monitorItemModel =
          normalizeModelId(
              getConfig().getString("resourceMode.monitor.itemModel", "terminal/monitor"),
              resourceNs);
      importBusItemModel =
          normalizeModelId(
              getConfig().getString("resourceMode.bus.import.itemModel", "bus/import"), resourceNs);
      exportBusItemModel =
          normalizeModelId(
              getConfig().getString("resourceMode.bus.export.itemModel", "bus/export"), resourceNs);
      wirelessItemModel =
          normalizeModelId(
              getConfig().getString("resourceMode.wirelessTerminal.itemModel", "terminal/wireless"),
              resourceNs);
      wirelessDisabledModel =
          normalizeModelId(
              getConfig()
                  .getString(
                      "resourceMode.wirelessTerminal.modelDisabledId",
                      "terminal/wireless_disabled"),
              resourceNs);
    } else {
      wireMaterial = Carriers.CARRIER_BARRIER;
      storageCarrier = Carriers.CARRIER_BARRIER;
      terminalCarrier = Carriers.CARRIER_BARRIER;
      monitorCarrier = Carriers.CARRIER_BARRIER;
      busCarrier = Carriers.CARRIER_BARRIER;
      wireItemModel =
          normalizeModelId(
              getConfig().getString("vanillaMode.wire.modelId", "black_stained_glass"), vanillaNs);
      storageItemModel =
          normalizeModelId(
              getConfig().getString("vanillaMode.storage.modelId", "vault"), vanillaNs);
      terminalItemModel =
          normalizeModelId(
              getConfig().getString("vanillaMode.terminal.modelId", "barrel"), vanillaNs);
      craftingTerminalItemModel =
          normalizeModelId(
              getConfig().getString("vanillaMode.craftingTerminal.modelId", "crafting_table"),
              vanillaNs);
      monitorItemModel =
          normalizeModelId(
              getConfig().getString("vanillaMode.monitor.itemModel", "smooth_stone"), vanillaNs);
      importBusItemModel =
          normalizeModelId(
              getConfig().getString("vanillaMode.importBus.itemModel", "dispenser"), vanillaNs);
      exportBusItemModel =
          normalizeModelId(
              getConfig().getString("vanillaMode.exportBus.itemModel", "dropper"), vanillaNs);
      wirelessItemModel =
          normalizeModelId(
              getConfig().getString("vanillaMode.wirelessTerminal.modelId", "target"), vanillaNs);
      wirelessDisabledModel =
          normalizeModelId(
              getConfig().getString("vanillaMode.wirelessTerminal.modelDisabledId", "target"),
              vanillaNs);
      resourceNs = vanillaNs; // reuse for display ids below
    }
    YamlConfiguration tiersConfig = loadStorageTiersConfig();
    StorageTier.loadFromConfig(tiersConfig.getConfigurationSection("tiers"), getLogger());
    storagePeekTicks = Math.max(1, getConfig().getLong("bossbar.storagePeekSeconds", 6) * 20L);
    wirePeekTicks = Math.max(1, getConfig().getLong("bossbar.wirePeekSeconds", 6) * 20L);
    var terminalConfig =
        resourceMode
            ? new ItemHologramManager.Config(false, 0.5, 0.95, 0.5, 0.35)
            : new ItemHologramManager.Config(
                getConfig().getBoolean("vanillaMode.terminalHologram.enabled", true),
                getConfig().getDouble("vanillaMode.terminalHologram.offset.x", 0.5),
                getConfig().getDouble("vanillaMode.terminalHologram.offset.y", 0.5),
                getConfig().getDouble("vanillaMode.terminalHologram.offset.z", 0.83),
                getConfig().getDouble("vanillaMode.terminalHologram.scale", 0.35));
    String storageHologramBase =
        resourceMode ? "resourceMode.storageHologram" : "vanillaMode.storageHologram";
    var storageConfig =
        new ItemHologramManager.Config(
            getConfig().getBoolean(storageHologramBase + ".enabled", true),
            getConfig().getDouble(storageHologramBase + ".offset.x", 0.5),
            getConfig().getDouble(storageHologramBase + ".offset.y", 0.5),
            getConfig().getDouble(storageHologramBase + ".offset.z", 0.5),
            getConfig().getDouble(storageHologramBase + ".scale", 0.35));
    String wirelessModel = wirelessItemModel;
    String wirelessDisabledModelFinal = wirelessDisabledModel;
    customItems =
        new CustomItems(
            keys,
            lang,
            wireItemModel,
            storageItemModel,
            terminalItemModel,
            craftingTerminalItemModel,
            monitorItemModel,
            importBusItemModel,
            exportBusItemModel,
            wirelessModel,
            wirelessDisabledModelFinal,
            vanillaNs + ":target");
    boolean wirelessEnabled = getConfig().getBoolean("wireless.enabled", true);
    int wirelessRange = getConfig().getInt("wireless.rangeChunks", 3);
    wirelessService =
        new WirelessTerminalService(this, keys, customItems, wirelessEnabled, wirelessRange);
    if (sessionManager != null) {
      sessionManager.reconfigure();
    }
    int wireLimitRaw = getConfig().getInt("wire.limit", getConfig().getInt("wireLimit", 32));
    wireLimit = Math.max(1, wireLimitRaw);
    int hardCapRaw =
        getConfig()
            .getInt(
                "wire.hardCap",
                getConfig().getInt("wireHardCap", Math.max(wireLimit * 2, wireLimit)));
    if (hardCapRaw < wireLimit) {
      getLogger()
          .warning("wireHardCap is ниже wireLimit; значение будет скорректировано до " + wireLimit);
    }
    wireHardCap = Math.max(wireLimit, hardCapRaw);
    if (networkGraphCache != null) {
      networkGraphCache.invalidateAll();
    }

    HandlerList.unregisterAll(this);
    setupRegionProtection();
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
    hologramManager =
        new ItemHologramManager(
            this,
            keys,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            terminalCarrier,
            terminalConfig,
            storageConfig);
    hologramManager.start();
    Bukkit.getPluginManager().registerEvents(hologramManager, this);

    Material base =
        resourceMode
            ? resolveMaterial(
                getConfig().getString("resourceMode.wire.displayBaseMaterial", "PAPER"),
                Material.PAPER)
            : Material.PAPER;
    double scale =
        resourceMode ? getConfig().getDouble("resourceMode.wire.displayScale", 1.0) : 1.0;
    double ox =
        resourceMode ? getConfig().getDouble("resourceMode.wire.displayOffset.x", 0.5) : 0.5;
    double oy =
        resourceMode ? getConfig().getDouble("resourceMode.wire.displayOffset.y", 0.5) : 0.5;
    double oz =
        resourceMode ? getConfig().getDouble("resourceMode.wire.displayOffset.z", 0.5) : 0.5;
    String ns =
        resourceMode ? getConfig().getString("resourceMode.namespace", "exort") : "minecraft";
    String center =
        resourceMode
            ? normalizeModelId(
                getConfig().getString("resourceMode.wire.displayModelCenter", "wire/center"), ns)
            : normalizeModelId(
                getConfig().getString("vanillaMode.wire.modelId", "black_stained_glass"), ns);
    String connection =
        resourceMode
            ? normalizeModelId(
                getConfig()
                    .getString("resourceMode.wire.displayModelConnection", "wire/connection"),
                ns)
            : "";
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
            center,
            connection,
            resourceMode,
            base,
            scale,
            ox,
            oy,
            oz,
            wireEntityName);
    if (wireDisplayManager.isEnabled()) {
      Bukkit.getScheduler().runTask(this, wireDisplayManager::scanLoadedChunks);
    }

    // Storage display manager
    Material storageBase =
        resourceMode
            ? resolveMaterial(
                getConfig().getString("resourceMode.storage.displayBaseMaterial", "PAPER"),
                Material.PAPER)
            : Material.PAPER;
    double storageScale =
        resourceMode ? getConfig().getDouble("resourceMode.storage.displayScale", 1.0) : 1.0;
    double sox =
        resourceMode ? getConfig().getDouble("resourceMode.storage.displayOffset.x", 0.5) : 0.5;
    double soy =
        resourceMode ? getConfig().getDouble("resourceMode.storage.displayOffset.y", 0.5) : 0.5;
    double soz =
        resourceMode ? getConfig().getDouble("resourceMode.storage.displayOffset.z", 0.5) : 0.5;
    String storageModel =
        normalizeModelId(
            getConfig()
                .getString(
                    resourceMode ? "resourceMode.storage.modelId" : "vanillaMode.storage.modelId",
                    resourceMode ? "storage/storage" : "vault"),
            ns);
    storageDisplayManager =
        new StorageDisplayManager(
            this, storageCarrier, storageModel, storageBase, storageScale, sox, soy, soz);
    Bukkit.getScheduler().runTask(this, storageDisplayManager::scanLoadedChunks);

    // Terminal display manager
    Material terminalBase =
        resourceMode
            ? resolveMaterial(
                getConfig().getString("resourceMode.terminal.displayBaseMaterial", "PAPER"),
                Material.PAPER)
            : Material.PAPER;
    double terminalScale =
        resourceMode ? getConfig().getDouble("resourceMode.terminal.displayScale", 1.0) : 1.0;
    double tox =
        resourceMode ? getConfig().getDouble("resourceMode.terminal.displayOffset.x", 0.5) : 0.5;
    double toy =
        resourceMode ? getConfig().getDouble("resourceMode.terminal.displayOffset.y", 0.5) : 0.5;
    double toz =
        resourceMode ? getConfig().getDouble("resourceMode.terminal.displayOffset.z", 0.5) : 0.5;
    String terminalModel =
        normalizeModelId(
            getConfig()
                .getString(
                    resourceMode ? "resourceMode.terminal.modelId" : "vanillaMode.terminal.modelId",
                    resourceMode ? "terminal/inventory" : "barrel"),
            ns);
    String terminalDisabledModel =
        normalizeModelId(
            getConfig()
                .getString(
                    resourceMode
                        ? "resourceMode.terminal.modelDisabledId"
                        : "vanillaMode.terminal.modelId",
                    resourceMode ? "terminal/inventory_disabled" : "barrel"),
            ns);
    String craftingTerminalModel =
        normalizeModelId(
            getConfig()
                .getString(
                    resourceMode
                        ? "resourceMode.craftingTerminal.modelId"
                        : "vanillaMode.craftingTerminal.modelId",
                    resourceMode ? "terminal/workbench" : "crafting_table"),
            ns);
    String craftingTerminalDisabledModel =
        normalizeModelId(
            getConfig()
                .getString(
                    resourceMode
                        ? "resourceMode.craftingTerminal.modelDisabledId"
                        : "vanillaMode.craftingTerminal.modelId",
                    resourceMode ? "terminal/workbench_disabled" : "crafting_table"),
            ns);
    String terminalEntityName = lang.tr("item.terminal");
    String craftingTerminalEntityName = lang.tr("item.crafting_terminal");
    terminalDisplayManager =
        new TerminalDisplayManager(
            this,
            terminalCarrier,
            terminalModel,
            terminalDisabledModel,
            craftingTerminalModel,
            craftingTerminalDisabledModel,
            terminalBase,
            terminalScale,
            tox,
            toy,
            toz,
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
    Material monitorBase =
        resourceMode
            ? resolveMaterial(
                getConfig().getString("resourceMode.monitor.displayBaseMaterial", "PAPER"),
                Material.PAPER)
            : Material.PAPER;
    double monitorScale =
        resourceMode ? getConfig().getDouble("resourceMode.monitor.displayScale", 1.0) : 1.0;
    double mox =
        resourceMode ? getConfig().getDouble("resourceMode.monitor.displayOffset.x", 0.5) : 0.5;
    double moy =
        resourceMode ? getConfig().getDouble("resourceMode.monitor.displayOffset.y", 0.5) : 0.5;
    double moz =
        resourceMode ? getConfig().getDouble("resourceMode.monitor.displayOffset.z", 0.5) : 0.5;
    String monitorModel =
        normalizeModelId(
            getConfig()
                .getString(
                    resourceMode ? "resourceMode.monitor.modelId" : "vanillaMode.monitor.modelId",
                    resourceMode ? "terminal/monitor" : "smooth_stone"),
            ns);
    String monitorDisabledModel =
        normalizeModelId(
            getConfig()
                .getString(
                    resourceMode
                        ? "resourceMode.monitor.modelDisabledId"
                        : "vanillaMode.monitor.modelId",
                    resourceMode ? "terminal/monitor_disabled" : "smooth_stone"),
            ns);
    String monitorItemPath =
        (resourceMode ? "resourceMode.monitor.screenItem" : "vanillaMode.monitor.screenItem");
    String monitorBlockPath =
        (resourceMode ? "resourceMode.monitor.screenBlock" : "vanillaMode.monitor.screenBlock");
    String monitorThinBlockPath =
        (resourceMode
            ? "resourceMode.monitor.screenThinBlock"
            : "vanillaMode.monitor.screenThinBlock");
    String monitorHorizontalBlockPath =
        (resourceMode
            ? "resourceMode.monitor.screenHorizontalBlock"
            : "vanillaMode.monitor.screenHorizontalBlock");
    String monitorFullBlockPath =
        (resourceMode
            ? "resourceMode.monitor.screenFullBlock"
            : "vanillaMode.monitor.screenFullBlock");
    double monitorItemY = resourceMode ? 0.56 : 0.62;
    double monitorItemZ = resourceMode ? 0.93 : 0.99;
    double monitorBlockY = resourceMode ? 0.56 : 0.62;
    double monitorBlockZ = resourceMode ? 0.93 : 0.99;
    double monitorThinY = resourceMode ? 0.56 : 0.62;
    double monitorThinZ = resourceMode ? 0.93 : 0.99;
    double monitorHorizontalY = resourceMode ? 0.55 : 0.61;
    double monitorHorizontalZ = resourceMode ? 1.026 : 1.032;
    double monitorFullY = resourceMode ? 0.56 : 0.62;
    double monitorFullZ = resourceMode ? 0.815 : 0.875;
    double monitorTextY = resourceMode ? 0.26 : 0.2;
    double monitorTextZ = resourceMode ? 0.95 : 1.01;
    double monitorTextScale = 0.55;
    double monitorTextEmptyY = 0.41;
    double monitorTextEmptyScale = resourceMode ? 0.7 : 0.8;
    var itemScreenConfig =
        new MonitorDisplayManager.ScreenConfig(
            getConfig().getDouble(monitorItemPath + ".offset.x", 0.5),
            getConfig().getDouble(monitorItemPath + ".offset.y", monitorItemY),
            getConfig().getDouble(monitorItemPath + ".offset.z", monitorItemZ),
            getConfig().getDouble(monitorItemPath + ".scale", 0.35));
    var blockScreenConfig =
        new MonitorDisplayManager.ScreenConfig(
            getConfig().getDouble(monitorBlockPath + ".offset.x", 0.5),
            getConfig().getDouble(monitorBlockPath + ".offset.y", monitorBlockY),
            getConfig().getDouble(monitorBlockPath + ".offset.z", monitorBlockZ),
            getConfig().getDouble(monitorBlockPath + ".scale", 0.6));
    var thinBlockScreenConfig =
        new MonitorDisplayManager.ScreenConfig(
            getConfig().getDouble(monitorThinBlockPath + ".offset.x", 0.5),
            getConfig().getDouble(monitorThinBlockPath + ".offset.y", monitorThinY),
            getConfig().getDouble(monitorThinBlockPath + ".offset.z", monitorThinZ),
            getConfig().getDouble(monitorThinBlockPath + ".scale", 0.3));
    var horizontalBlockScreenConfig =
        new MonitorDisplayManager.ScreenConfig(
            getConfig().getDouble(monitorHorizontalBlockPath + ".offset.x", 0.5),
            getConfig().getDouble(monitorHorizontalBlockPath + ".offset.y", monitorHorizontalY),
            getConfig().getDouble(monitorHorizontalBlockPath + ".offset.z", monitorHorizontalZ),
            getConfig().getDouble(monitorHorizontalBlockPath + ".scale", 0.4));
    var fullBlockScreenConfig =
        new MonitorDisplayManager.ScreenConfig(
            getConfig().getDouble(monitorFullBlockPath + ".offset.x", 0.5),
            getConfig().getDouble(monitorFullBlockPath + ".offset.y", monitorFullY),
            getConfig().getDouble(monitorFullBlockPath + ".offset.z", monitorFullZ),
            getConfig().getDouble(monitorFullBlockPath + ".scale", 0.58));
    var textScreenConfig =
        new MonitorDisplayManager.ScreenConfig(
            getConfig()
                .getDouble(
                    (resourceMode
                            ? "resourceMode.monitor.screenText"
                            : "vanillaMode.monitor.screenText")
                        + ".offset.x",
                    0.51),
            getConfig()
                .getDouble(
                    (resourceMode
                            ? "resourceMode.monitor.screenText"
                            : "vanillaMode.monitor.screenText")
                        + ".offset.y",
                    monitorTextY),
            getConfig()
                .getDouble(
                    (resourceMode
                            ? "resourceMode.monitor.screenText"
                            : "vanillaMode.monitor.screenText")
                        + ".offset.z",
                    monitorTextZ),
            getConfig()
                .getDouble(
                    (resourceMode
                            ? "resourceMode.monitor.screenText"
                            : "vanillaMode.monitor.screenText")
                        + ".scale",
                    monitorTextScale));
    String textEmptyPath =
        (resourceMode
            ? "resourceMode.monitor.screenTextEmpty"
            : "vanillaMode.monitor.screenTextEmpty");
    var textEmptyScreenConfig =
        new MonitorDisplayManager.ScreenConfig(
            getConfig().getDouble(textEmptyPath + ".offset.x", textScreenConfig.offsetX()),
            getConfig().getDouble(textEmptyPath + ".offset.y", monitorTextEmptyY),
            getConfig().getDouble(textEmptyPath + ".offset.z", textScreenConfig.offsetZ()),
            getConfig().getDouble(textEmptyPath + ".scale", monitorTextEmptyScale));
    int textAlpha = 0;
    String monitorName = lang.tr("item.monitor");
    monitorDisplayManager =
        new MonitorDisplayManager(
            this,
            keys,
            storageManager,
            monitorCarrier,
            monitorModel,
            monitorDisabledModel,
            monitorBase,
            monitorScale,
            mox,
            moy,
            moz,
            monitorName,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            itemScreenConfig,
            blockScreenConfig,
            thinBlockScreenConfig,
            horizontalBlockScreenConfig,
            fullBlockScreenConfig,
            textScreenConfig,
            textEmptyScreenConfig,
            textAlpha);
    Bukkit.getScheduler().runTask(this, monitorDisplayManager::start);

    // Bus display manager
    Material busBase =
        resourceMode
            ? resolveMaterial(
                getConfig().getString("resourceMode.bus.displayBaseMaterial", "PAPER"),
                Material.PAPER)
            : Material.PAPER;
    double busScale =
        resourceMode ? getConfig().getDouble("resourceMode.bus.displayScale", 1.0) : 1.0;
    double box =
        resourceMode ? getConfig().getDouble("resourceMode.bus.displayOffset.x", 0.5) : 0.5;
    double boy =
        resourceMode ? getConfig().getDouble("resourceMode.bus.displayOffset.y", 0.5) : 0.5;
    double boz =
        resourceMode ? getConfig().getDouble("resourceMode.bus.displayOffset.z", 0.5) : 0.5;
    String importBusModel =
        normalizeModelId(
            getConfig()
                .getString(
                    resourceMode
                        ? "resourceMode.bus.import.modelId"
                        : "vanillaMode.importBus.modelId",
                    resourceMode ? "bus/import" : "dispenser"),
            ns);
    String exportBusModel =
        normalizeModelId(
            getConfig()
                .getString(
                    resourceMode
                        ? "resourceMode.bus.export.modelId"
                        : "vanillaMode.exportBus.modelId",
                    resourceMode ? "bus/export" : "dropper"),
            ns);
    String importBusName = lang.tr("item.import_bus");
    String exportBusName = lang.tr("item.export_bus");
    busDisplayManager =
        new BusDisplayManager(
            this,
            busCarrier,
            importBusModel,
            exportBusModel,
            busBase,
            busScale,
            box,
            boy,
            boz,
            importBusName,
            exportBusName);
    Bukkit.getScheduler().runTask(this, busDisplayManager::scanLoadedChunks);

    displayRefreshService =
        new DisplayRefreshService(
            this,
            wireDisplayManager,
            storageDisplayManager,
            terminalDisplayManager,
            monitorDisplayManager,
            busDisplayManager);
    var chunkSanityService =
        new ChunkSanityService(
            new DisplayCleanupService(
                this, wireMaterial, storageCarrier, terminalCarrier, monitorCarrier, busCarrier),
            new MarkerSanityService(
                this,
                displayRefreshService,
                wireMaterial,
                storageCarrier,
                terminalCarrier,
                monitorCarrier,
                busCarrier),
            displayRefreshService);
    // Unified sanity listener (handles carrier changes and display refresh)
    var chunkSanityListener = new ChunkSanityListener(this, chunkSanityService);
    Bukkit.getPluginManager().registerEvents(chunkSanityListener, this);

    // Schedule sanity scan for loaded chunks to update carriers/displays if materials changed
    if (!sanityScanScheduled) {
      sanityScanScheduled = true;
      Bukkit.getScheduler().runTask(this, chunkSanityService::scanLoadedChunks);
    }

    int busActiveTicks = getConfig().getInt("bus.activeIntervalTicks", 5);
    int busIdleTicks = getConfig().getInt("bus.idleIntervalTicks", 40);
    int busItemsPerOp = getConfig().getInt("bus.itemsPerOperation", 1);
    int busMaxOps = getConfig().getInt("bus.maxOperationsPerTick", 6000);
    int busMaxOpsPerChunk = getConfig().getInt("bus.maxOperationsPerChunk", 600);
    boolean allowStorageTargets = getConfig().getBoolean("bus.allowStorageTargets", true);
    busService =
        new BusService(
            this,
            storageManager,
            database,
            busCarrier,
            busActiveTicks,
            busIdleTicks,
            busItemsPerOp,
            busMaxOps,
            busMaxOpsPerChunk,
            allowStorageTargets,
            wirelessService);
    busService.start();
    Bukkit.getScheduler().runTask(this, busService::scanLoadedChunks);
    busSessionManager = new BusSessionManager(this, busService, lang);

    breakHandler =
        new BlockBreakHandler(
            this,
            customItems,
            wireMaterial,
            storageCarrier,
            terminalCarrier,
            monitorCarrier,
            busCarrier,
            hologramManager,
            wireDisplayManager,
            displayRefreshService);
    var breakConfig = BreakConfig.fromConfig(getConfig(), getLogger());
    breakSoundConfig = BreakSoundConfig.fromConfig(getConfig());
    customBlockBreaker =
        new CustomBlockBreaker(
            this,
            breakHandler,
            breakConfig,
            breakSoundConfig,
            wireMaterial,
            storageCarrier,
            terminalCarrier,
            busCarrier);
    customBlockBreaker.start();
    Bukkit.getPluginManager().registerEvents(customBlockBreaker, this);
    Bukkit.getPluginManager()
        .registerEvents(
            new BlockListener(
                this,
                storageManager,
                keys,
                customItems,
                wireMaterial,
                hologramManager,
                wireDisplayManager,
                storageCarrier,
                terminalCarrier,
                monitorCarrier,
                busCarrier,
                breakHandler),
            this);
    Bukkit.getPluginManager()
        .registerEvents(
            new TerminalListener(
                this,
                storageManager,
                sessionManager,
                keys,
                lang,
                wireLimit,
                wireHardCap,
                wireMaterial,
                storageCarrier,
                terminalCarrier),
            this);
    Bukkit.getPluginManager()
        .registerEvents(new BusListener(this, busSessionManager, busCarrier), this);
    Bukkit.getPluginManager()
        .registerEvents(
            new InventoryEvents(sessionManager, storageManager, busSessionManager), this);
    dialogSupported = detectDialogSupport();
    Bukkit.getPluginManager()
        .registerEvents(new SearchDialogListener(sessionManager, searchDialogService, this), this);
    Bukkit.getPluginManager()
        .registerEvents(new StorageListener(this, storagePeekTicks, storageCarrier), this);
    Bukkit.getPluginManager()
        .registerEvents(
            new WireListener(
                this, keys, wireLimit, wireHardCap, wireMaterial, wirePeekTicks, storageCarrier),
            this);
    Bukkit.getPluginManager()
        .registerEvents(
            new PickListener(
                this,
                customItems,
                keys,
                wireMaterial,
                storageCarrier,
                terminalCarrier,
                monitorCarrier,
                busCarrier),
            this);
    Bukkit.getPluginManager()
        .registerEvents(
            new ItemPlaceBridgeListener(
                this,
                storageManager,
                customItems,
                keys,
                wireMaterial,
                storageCarrier,
                terminalCarrier,
                monitorCarrier,
                busCarrier),
            this);
    Bukkit.getPluginManager().registerEvents(new MonitorListener(this, monitorCarrier), this);
    Bukkit.getPluginManager().registerEvents(new InventoryRefreshListener(this), this);
    boolean blockVanilla = getConfig().getBoolean("crafting.blockVanilla", true);
    boolean allowExternal = getConfig().getBoolean("crafting.allowExternal", true);
    if (recipeService != null) {
      recipeService.unregisterAll();
    }
    craftingRules = new CraftingRules(keys, blockVanilla, allowExternal);
    Bukkit.getPluginManager().registerEvents(new CraftBlockerListener(craftingRules), this);
    recipeService = new RecipeService(this, customItems, wirelessService);
    recipeService.reload();
    Bukkit.getPluginManager()
        .registerEvents(new WirelessListener(this, wirelessService, storageManager), this);
    Bukkit.getPluginManager().registerEvents(new WirelessCraftListener(wirelessService), this);
    // Ensure terminals/monitors refresh after mode switch so displays don't stay disabled until
    // interaction.
    Bukkit.getScheduler()
        .runTaskLater(
            this,
            () -> {
              if (networkGraphCache != null) {
                networkGraphCache.invalidateAll();
              }
              String namespace = getName().toLowerCase(Locale.ROOT);
              for (var world : Bukkit.getWorlds()) {
                for (var chunk : world.getLoadedChunks()) {
                  displayRefreshService.refreshChunk(chunk);
                  var keys = chunk.getPersistentDataContainer().getKeys();
                  if (keys.isEmpty()) continue;
                  for (var key : keys) {
                    if (!key.getNamespace().equals(namespace)) continue;
                    String raw = key.getKey();
                    if (!raw.startsWith("storage_")) continue;
                    int[] xyz = MarkerCoords.parseXYZ(raw.substring("storage_".length()));
                    if (xyz == null) continue;
                    var block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
                    displayRefreshService.refreshNetworkFrom(block);
                  }
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
    scheduleFlushTask();
    scheduleCacheEviction();
    return langFuture;
  }

  public long getStoragePeekTicks() {
    return storagePeekTicks;
  }

  public RegionProtection getRegionProtection() {
    return regionProtection;
  }

  private void switchToResourceOnFirstRun() {
    File dbFile = new File(new File(getDataFolder(), "db"), "storage.db");
    if (dbFile.exists()) return;
    var nexo = getServer().getPluginManager().getPlugin("Nexo");
    if (nexo == null || !nexo.isEnabled()) return;
    String mode = getConfig().getString("mode", "VANILLA");
    if ("RESOURCE".equalsIgnoreCase(mode)) return;
    getConfig().set("mode", "RESOURCE");
    saveConfig();
  }

  private void enforcePaperChorusSetting() {
    boolean modeVanilla = "VANILLA".equalsIgnoreCase(getConfig().getString("mode", "VANILLA"));
    if (isChorusUpdatesDisabled()) return;
    if (modeVanilla) return;
    var console = Bukkit.getConsoleSender();
    console.sendMessage(
        Component.text(
            "[Exort] Paper's block-updates.disable-chorus-plant-updates is not enabled.",
            NamedTextColor.RED,
            TextDecoration.BOLD));
    console.sendMessage(
        Component.text(
            "It is HIGHLY recommended to enable this setting for improved performance and prevent"
                + " bugs with chorus-plants which are used to display wires by default in RESOURCE"
                + " mode.",
            NamedTextColor.RED));
    console.sendMessage(
        Component.text(
            "Until then, the plugin's operating mode is forced to VANILLA.", NamedTextColor.RED));
    getConfig().set("mode", "VANILLA");
    saveConfig();
  }

  public boolean canEnableResourceMode() {
    return isChorusUpdatesDisabled();
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
    File serverRoot =
        getDataFolder().getParentFile() != null
            ? getDataFolder().getParentFile().getParentFile()
            : null;
    File paperConfig =
        serverRoot != null
            ? new File(new File(serverRoot, "config"), "paper-global.yml")
            : new File("config/paper-global.yml");
    if (!paperConfig.isFile()) {
      paperConfig =
          serverRoot != null
              ? new File(serverRoot, "paper-global.yml")
              : new File("paper-global.yml");
    }
    if (!paperConfig.isFile()) {
      return false;
    }
    YamlConfiguration paper = YamlConfiguration.loadConfiguration(paperConfig);
    return paper.getBoolean("block-updates.disable-chorus-plant-updates", false);
  }

  private void setupRegionProtection() {
    regionProtection = RegionProtection.allowAll();
    if (!getConfig().getBoolean("worldguard.enabled", true)) {
      return;
    }
    var wg = getServer().getPluginManager().getPlugin("WorldGuard");
    if (wg == null || !wg.isEnabled()) return;
    try {
      regionProtection = new WorldGuardProtection();
    } catch (IllegalStateException ignored) {
      // WorldGuard classes are unavailable; keep allow-all behavior.
    }
    boolean debug = getConfig().getBoolean("worldguard.debug", false);
    if (debug) {
      regionProtection = new DebugRegionProtection(regionProtection, getLogger(), true);
    }
  }

  private void registerBrigadierCommands() {
    getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event -> {
              event
                  .registrar()
                  .register(
                      new ExortBrigadier(this).build(),
                      "Exort Storage Network admin commands",
                      List.of("esn", "vst"));
            });
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

  public NetworkGraphCache getNetworkGraphCache() {
    return networkGraphCache;
  }

  public int getWireLimit() {
    return wireLimit;
  }

  public int getWireHardCap() {
    return wireHardCap;
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

  private void scheduleCacheEviction() {
    if (cacheEvictTaskId != -1) {
      Bukkit.getScheduler().cancelTask(cacheEvictTaskId);
      cacheEvictTaskId = -1;
    }
    if (storageManager == null) return;
    long idleSeconds = getConfig().getLong("cache.idleUnloadSeconds", 300);
    long checkSeconds = getConfig().getLong("cache.idleCheckSeconds", 60);
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
    int flushSeconds = getConfig().getInt("flushIntervalSeconds", 10);
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
      getLogger().warning("Failed to load storage-tiers.yml: " + e.getMessage());
    }
    return cfg;
  }

  private Material resolveMaterial(String name, Material fallback) {
    if (name == null) {
      if (fallback != null) {
        getLogger().warning("Invalid material 'null', falling back to " + fallback);
      } else {
        getLogger().warning("Invalid material 'null'");
      }
      return fallback;
    }
    String raw = name.trim();
    if (raw.isEmpty()) {
      if (fallback != null) {
        getLogger().warning("Invalid material '" + name + "', falling back to " + fallback);
      } else {
        getLogger().warning("Invalid material '" + name + "'");
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
        getLogger().warning("Invalid material '" + name + "', falling back to " + fallback);
      } else {
        getLogger().warning("Invalid material '" + name + "'");
      }
      return fallback;
    }
  }

  private String normalizeModelId(String raw, String namespace) {
    String ns = namespace == null || namespace.isBlank() ? "minecraft" : namespace.trim();
    String id = raw == null ? "" : raw.trim();
    if (id.isEmpty()) id = "unknown";
    id = id.toLowerCase(Locale.ROOT);
    int colon = id.indexOf(':');
    if (colon >= 0 && colon + 1 < id.length()) {
      id = id.substring(colon + 1);
    }
    id = ns + ":" + id;
    return id;
  }

  // materialFromModel removed: item materials are always PAPER
}
