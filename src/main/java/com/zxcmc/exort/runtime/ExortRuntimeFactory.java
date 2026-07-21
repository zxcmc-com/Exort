package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BreakAnimationSender;
import com.zxcmc.exort.breaking.BreakConfig;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.chunkloader.ChunkLoaderAuditFileWriter;
import com.zxcmc.exort.chunkloader.ChunkLoaderAuditLogger;
import com.zxcmc.exort.chunkloader.ChunkLoaderConfig;
import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.chunkloader.RotatingChunkLoaderAuditFileWriter;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.gui.CreativeTabOrder;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.config.ConfigNumbers;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.auth.KnownAuthenticationGate;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.integration.protocol.PacketEnhancementsFactory;
import com.zxcmc.exort.integration.worldedit.WorldEditBridgeDependencies;
import com.zxcmc.exort.integration.worldedit.WorldEditBulkConfig;
import com.zxcmc.exort.integration.worldedit.WorldEditIntegration;
import com.zxcmc.exort.integration.worldedit.WorldEditRuntimeBootstrap;
import com.zxcmc.exort.integration.worldedit.wand.KnownWorldEditWandGuard;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.placement.PlacementGuardConfig;
import com.zxcmc.exort.recipes.CraftingRulesConfig;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.relay.RelaySetupTracker;
import com.zxcmc.exort.storage.StorageClaimRegistry;
import com.zxcmc.exort.storage.StorageRuntimeConfig;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.io.File;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExortRuntimeFactory {
  private ExortRuntimeFactory() {}

  /** Parses the candidate's runtime-owned configuration before the active generation is stopped. */
  public static PreparedRuntime preflight(
      JavaPlugin plugin,
      FileConfiguration config,
      boolean resourceMode,
      boolean resourceWireUsesBarrier) {
    if (plugin == null || config == null) {
      throw new IllegalArgumentException("plugin and config are required");
    }
    RuntimeItemModelConfig itemModels =
        RuntimeItemModelConfig.forMode(resourceMode, resourceWireUsesBarrier);
    ConfigNumbers numbers = new ConfigNumbers(config, plugin.getLogger());
    RuntimeNetworkConfig network = RuntimeNetworkConfig.fromNumbers(config, numbers);
    WirelessRuntimeConfig wireless = WirelessRuntimeConfig.fromNumbers(config, numbers);
    BusRuntimeConfig bus = BusRuntimeConfig.fromNumbers(config, numbers);
    StorageRuntimeConfig storage = StorageRuntimeConfig.fromNumbers(config, numbers);
    GuiRuntimeConfig gui = GuiRuntimeConfig.fromConfig(numbers);
    ChunkLoaderConfig chunkLoader = ChunkLoaderConfig.fromNumbers(config, numbers);
    BreakConfig breaking = BreakConfig.fromConfig(config, plugin.getLogger());
    PlacementGuardConfig placementGuard = PlacementGuardConfig.fromConfig(config);
    WorldEditBulkConfig worldEdit = WorldEditBulkConfig.fromConfig(config);

    File tiersFile = new File(plugin.getDataFolder(), "storage-tiers.yml");
    if (!tiersFile.isFile()) {
      throw new IllegalStateException("storage-tiers.yml does not exist");
    }
    YamlConfiguration tiers = new YamlConfiguration();
    try {
      tiers.load(tiersFile);
    } catch (Exception error) {
      throw new IllegalStateException("storage-tiers.yml is invalid", error);
    }
    ConfigurationSection tierSection = tiers.getConfigurationSection("tiers");
    StorageTierCatalog tierCatalog;
    try {
      tierCatalog = StorageTierCatalog.parse(tierSection, plugin.getLogger());
    } catch (IllegalArgumentException invalidCatalog) {
      throw new IllegalStateException(
          "storage-tiers.yml does not contain a valid catalog", invalidCatalog);
    }
    return new PreparedRuntime(
        itemModels,
        network,
        wireless,
        bus,
        storage,
        gui,
        chunkLoader,
        breaking,
        placementGuard,
        worldEdit,
        tierCatalog,
        RecipeService.prepare(plugin, config));
  }

  public static RuntimeHandle<ExortRuntimeServices> create(
      ExortRuntimeFactoryDependencies deps, boolean refreshItemDictionaries) {
    RuntimeHandle.Scope scope = RuntimeHandle.scope();
    try {
      ExortRuntimeServices services = createServices(deps, refreshItemDictionaries, scope);
      return scope.complete(services);
    } catch (RuntimeException | LinkageError constructionFailure) {
      try {
        scope.close();
      } catch (RuntimeException | LinkageError cleanupFailure) {
        throw new RuntimeConstructionException(constructionFailure, cleanupFailure);
      }
      throw constructionFailure;
    }
  }

  private static ExortRuntimeServices createServices(
      ExortRuntimeFactoryDependencies deps,
      boolean refreshItemDictionaries,
      RuntimeHandle.Scope scope) {
    RuntimeGenerationScope generation = new RuntimeGenerationScope(deps.plugin());
    scope.own("runtime generation fallback", generation::close);
    deps.reloadDefaultSortMode().run();
    RuntimeItemNamesReload itemNamesReload = prepareLanguage(deps, refreshItemDictionaries);
    CreativeTabOrder.init(deps.plugin(), deps.keys());

    RuntimeItemModelConfig itemModels = deps.preparedRuntime().itemModels();
    CarrierMaterials materials =
        new CarrierMaterials(
            itemModels.wireMaterial(),
            itemModels.storageCarrier(),
            itemModels.terminalCarrier(),
            itemModels.monitorCarrier(),
            itemModels.busCarrier(),
            itemModels.relayCarrier(),
            itemModels.transmitterCarrier(),
            itemModels.chunkLoaderCarrier());

    RuntimeNetworkConfig networkConfig = deps.preparedRuntime().network();
    WirelessRuntimeConfig wirelessConfig = deps.preparedRuntime().wireless();
    if (networkConfig.wireHardCapAdjusted()) {
      ExortLog.warn(
          "wireHardCap is below wireLimit; value will be adjusted to " + networkConfig.wireLimit());
    }
    if (deps.networkGraphCache().get() != null) {
      deps.networkGraphCache().get().invalidateAll();
    }
    var relayTraversalCarrier = networkConfig.relayEnabled() ? materials.relayCarrier() : null;

    CustomItems customItems = createCustomItems(deps, itemModels, wirelessConfig);
    WirelessTerminalService wirelessService =
        createWirelessService(deps, customItems, wirelessConfig);
    WirelessTransmitterService wirelessTransmitterService =
        createWirelessTransmitterService(deps, materials, networkConfig, wirelessConfig);
    RuntimeServiceState state = new RuntimeServiceState();
    TransmitterSessionManager transmitterSessionManager =
        new TransmitterSessionManager(
            deps.plugin(),
            wirelessTransmitterService,
            wirelessService,
            customItems,
            deps.lang(),
            deps.playerFeedback(),
            deps.regionProtection().get(),
            () -> deps.resourceMode(),
            deps.guiOverlayConfig(),
            block -> {
              DisplayRefreshService refresh = state.displayRefreshService;
              if (refresh != null) {
                refresh.refreshTransmitter(block);
              }
            });
    scope.own("transmitter sessions", transmitterSessionManager::shutdown);
    transmitterSessionManager.reconfigure();
    wirelessTransmitterService.scanLoadedChunks();
    deps.sessionManager().reconfigure();

    PacketEnhancements packetEnhancements =
        PacketEnhancementsFactory.tryCreate(deps.plugin(), deps.pickDebugFullSink());
    if (packetEnhancements != null) {
      scope.own("packet enhancements", packetEnhancements::unregister);
    }
    ChunkLoaderConfig chunkLoaderConfig = deps.preparedRuntime().chunkLoader();
    ChunkLoaderAuditFileWriter chunkLoaderAuditFileWriter =
        chunkLoaderConfig.audit().shouldWriteFile()
            ? RotatingChunkLoaderAuditFileWriter.create(
                deps.plugin().getDataFolder().toPath(),
                chunkLoaderConfig.audit().file(),
                deps.plugin().getLogger())
            : ChunkLoaderAuditFileWriter.noop();
    ChunkLoaderService chunkLoaderService =
        new ChunkLoaderService(
            deps.plugin(),
            deps.database(),
            materials.chunkLoaderCarrier(),
            chunkLoaderConfig,
            new ChunkLoaderAuditLogger(
                deps.plugin().getLogger(), chunkLoaderConfig.audit(), chunkLoaderAuditFileWriter));
    scope.own("chunk loader", chunkLoaderService::stop);
    chunkLoaderService.start();
    StorageClaimRegistry storageClaimRegistry =
        new StorageClaimRegistry(deps.database(), deps.plugin().getLogger());
    scope.own("storage claim registry", storageClaimRegistry::close);
    storageClaimRegistry.start();
    BreakAnimationSender breakAnimationSender =
        RuntimeBreakAnimationSenders.create(
            deps.plugin(), deps.resourceMode(), itemModels.displayNamespace(), materials);
    deps.setupRegionProtection().run();
    AuthenticationGate authenticationGate = new KnownAuthenticationGate(deps.plugin());
    WorldEditWandGuard worldEditWandGuard = new KnownWorldEditWandGuard(deps.plugin());

    RelaySetupTracker relaySetupTracker =
        new RelaySetupTracker(
            deps.plugin(),
            RelaySetupTracker.DEFAULT_TTL_MS,
            block -> {
              DisplayRefreshService refresh = state.displayRefreshService;
              if (refresh != null) {
                refresh.refreshRelay(block);
                refresh.refreshBlockAndNeighbors(block);
                refresh.refreshNetworkFrom(block);
              }
            });
    scope.own("relay setup tracker", relaySetupTracker::stop);
    RuntimeHologramConfig hologramConfig = RuntimeHologramConfig.forMode(deps.resourceMode());
    RuntimeDisplayServices displayServices =
        RuntimeDisplayServicesFactory.create(
            new RuntimeDisplayServicesDependencies(
                deps.plugin(),
                deps.config(),
                deps.lang(),
                deps.keys(),
                wirelessConfig,
                deps.storageTierCatalog(),
                deps.storageManager(),
                deps.database(),
                materials,
                itemModels,
                hologramConfig,
                deps.resourceMode(),
                relaySetupTracker,
                networkConfig.wireLimit(),
                networkConfig.wireHardCap(),
                relayTraversalCarrier,
                networkConfig.relayRangeChunks(),
                packetEnhancements,
                deps.worldEditDebugService(),
                () -> state.busService,
                () -> wirelessTransmitterService,
                () -> transmitterSessionManager,
                deps.networkGraphCache(),
                chunk -> {
                  if (deps.networkGraphCache().get() != null) {
                    deps.networkGraphCache().get().invalidateChunk(chunk);
                  }
                }),
            generation,
            scope);
    state.displayRefreshService = displayServices.displayRefreshService();

    BusRuntimeConfig busRuntime = deps.preparedRuntime().bus();
    RuntimeBusServices busServices =
        RuntimeBusServicesFactory.create(
            new RuntimeBusServicesDependencies(
                deps.plugin(),
                deps.keys(),
                deps.bossBarManager(),
                deps.storageManager(),
                deps.database(),
                wirelessService,
                deps.lang(),
                deps.itemNameService(),
                materials,
                networkConfig.wireLimit(),
                networkConfig.wireHardCap(),
                relayTraversalCarrier,
                networkConfig.relayRangeChunks(),
                busRuntime,
                () -> deps.resourceMode(),
                deps.guiRuntimeConfig(),
                deps.guiOverlayConfig(),
                deps.networkGraphCache(),
                deps.renderStorage()),
            generation,
            scope);
    state.busService = busServices.busService();

    RuntimeBreakingServices breakingServices =
        RuntimeBreakingServicesFactory.create(
            new RuntimeBreakingServicesDependencies(
                deps.plugin(),
                deps.config(),
                deps.plugin().getLogger(),
                customItems,
                materials,
                displayServices.hologramManager(),
                displayServices.wireDisplayManager(),
                displayServices.displayRefreshService(),
                deps.storageManager(),
                storageClaimRegistry,
                deps.sessionManager(),
                () -> displayServices.monitorDisplayManager(),
                () -> busServices.busSessionManager(),
                () -> busServices.busService(),
                deps.networkGraphCache(),
                deps.regionProtection().get(),
                worldEditWandGuard,
                deps.playerFeedback(),
                breakAnimationSender,
                deps.preparedRuntime().breaking(),
                wirelessTransmitterService,
                transmitterSessionManager,
                chunkLoaderService,
                packetEnhancements));
    scope.own("custom block breaker", breakingServices.customBlockBreaker()::shutdown);

    RuntimeListenerRegistration listenerRegistration =
        RuntimeListenerRegistrar.register(
            new RuntimeListenerDependencies(
                deps.core(),
                deps.runtimeConfig(),
                materials,
                networkConfig,
                new RuntimeListenerDomainServices(
                    storageClaimRegistry,
                    customItems,
                    wirelessService,
                    wirelessTransmitterService,
                    transmitterSessionManager,
                    chunkLoaderService,
                    relaySetupTracker),
                displayServices,
                busServices,
                breakingServices,
                new RuntimeListenerIntegrations(
                    deps.regionProtection().get(),
                    authenticationGate,
                    worldEditWandGuard,
                    packetEnhancements,
                    deps.networkGraphCache(),
                    deps.recipeActivation()),
                new RuntimeListenerPolicies(
                    busRuntime, CraftingRulesConfig.defaults(), relayTraversalCarrier),
                deps.hooks(),
                generation,
                deps.storageTierCatalog(),
                scope),
            deps.preparedRuntime().placementGuard());

    RuntimePostRefreshScheduler.Registration postRefresh =
        RuntimePostRefreshScheduler.schedule(
            new RuntimePostRefreshScheduler.RuntimePostRefreshDependencies(
                deps.plugin(),
                deps.networkGraphCache(),
                displayServices.displayRefreshService(),
                deps.storageManager(),
                deps.inventoryRefreshService()));
    scope.own("post-runtime refresh", postRefresh::close);
    WorldEditBridgeDependencies worldEditDependencies =
        new WorldEditBridgeDependencies(
            deps.plugin(),
            deps.database(),
            deps.storageManager(),
            storageClaimRegistry,
            deps.worldEditDebugService(),
            deps.networkGraphCache(),
            displayServices::displayRefreshService,
            busServices::busService,
            () -> chunkLoaderService,
            () -> wirelessTransmitterService,
            () -> transmitterSessionManager,
            displayServices::hologramManager,
            deps.storageTierCatalog(),
            materials,
            deps.preparedRuntime().worldEdit(),
            deps.config().getBoolean("integrations.fawe.autoConfigure", false));
    WorldEditRuntimeBootstrap.Registration worldEditRegistration =
        WorldEditRuntimeBootstrap.register(
            worldEditDependencies, deps.tryRegisterWorldEdit(), deps.worldEditIntegrationSink());
    scope.own("WorldEdit integration lifecycle", worldEditRegistration::close);
    WorldEditIntegration worldEditIntegration = worldEditRegistration.current();
    if (deps.runtimeTasks() != null) {
      deps.runtimeTasks().schedule(deps.preparedRuntime().storage());
      scope.own("runtime tasks", deps.runtimeTasks()::cancel);
    }
    scope.own("runtime generation", generation::close);

    return new ExortRuntimeServices(
        itemNamesReload,
        customItems,
        wirelessService,
        wirelessTransmitterService,
        transmitterSessionManager,
        chunkLoaderService,
        storageClaimRegistry,
        relaySetupTracker,
        materials,
        relayTraversalCarrier,
        networkConfig.wireLimit(),
        networkConfig.wireHardCap(),
        networkConfig.relayRangeChunks(),
        networkConfig.storagePeekTicks(),
        networkConfig.wirePeekTicks(),
        displayServices.hologramManager(),
        displayServices.wireDisplayManager(),
        displayServices.storageDisplayManager(),
        displayServices.terminalDisplayManager(),
        displayServices.monitorDisplayManager(),
        displayServices.busDisplayManager(),
        displayServices.blockProxyService(),
        displayServices.displayCullingService(),
        displayServices.displayRefreshService(),
        busServices.busService(),
        busServices.busSessionManager(),
        breakingServices.breakHandler(),
        breakingServices.customBlockBreaker(),
        breakingServices.breakSoundConfig(),
        listenerRegistration.craftingRules(),
        listenerRegistration.recipeService(),
        packetEnhancements,
        listenerRegistration.placementGuard(),
        worldEditIntegration);
  }

  private static RuntimeItemNamesReload prepareLanguage(
      ExortRuntimeFactoryDependencies deps, boolean refreshItemDictionaries) {
    String langCode = deps.config().getString("language", "en_us");
    String normalized = deps.itemNameService().normalizeLanguage(langCode);
    Lang lang = deps.lang();
    lang.reload(normalized);
    deps.searchDialogService().invalidate();
    return new RuntimeItemNamesReload(
        () ->
            refreshItemDictionaries
                ? deps.itemNameService().refresh(normalized)
                : deps.itemNameService().reload(normalized));
  }

  private static CustomItems createCustomItems(
      ExortRuntimeFactoryDependencies deps,
      RuntimeItemModelConfig itemModels,
      WirelessRuntimeConfig wirelessConfig) {
    return new CustomItems(
        deps.keys(),
        deps.lang(),
        itemModels.customItemModels(),
        wirelessConfig,
        deps.resourceMode(),
        deps.storageTierCatalog());
  }

  private static WirelessTerminalService createWirelessService(
      ExortRuntimeFactoryDependencies deps,
      CustomItems customItems,
      WirelessRuntimeConfig wirelessConfig) {
    return new WirelessTerminalService(
        deps.lang(),
        deps.keys(),
        customItems,
        wirelessConfig.enabled(),
        wirelessConfig.rangeBlocks(),
        deps.storageTierCatalog());
  }

  private static WirelessTransmitterService createWirelessTransmitterService(
      ExortRuntimeFactoryDependencies deps,
      CarrierMaterials materials,
      RuntimeNetworkConfig networkConfig,
      WirelessRuntimeConfig wirelessConfig) {
    return new WirelessTransmitterService(
        deps.plugin(),
        deps.keys(),
        wirelessConfig,
        networkConfig.wireLimit(),
        networkConfig.wireHardCap(),
        networkConfig.relayRangeChunks(),
        materials.wire(),
        materials.storageCarrier(),
        materials.transmitterCarrier(),
        networkConfig.relayEnabled() ? materials.relayCarrier() : null,
        deps.networkGraphCache());
  }

  private static final class RuntimeServiceState {
    private BusService busService;
    private DisplayRefreshService displayRefreshService;
  }
}
