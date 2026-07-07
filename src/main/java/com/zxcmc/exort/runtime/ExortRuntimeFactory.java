package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BreakAnimationSender;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.chunkloader.ChunkLoaderAuditFileWriter;
import com.zxcmc.exort.chunkloader.ChunkLoaderAuditLogger;
import com.zxcmc.exort.chunkloader.ChunkLoaderConfig;
import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.chunkloader.RotatingChunkLoaderAuditFileWriter;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.gui.CreativeTabOrder;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.auth.KnownAuthenticationGate;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.integration.protocol.PacketEnhancementsFactory;
import com.zxcmc.exort.integration.worldedit.WorldEditBridgeDependencies;
import com.zxcmc.exort.integration.worldedit.WorldEditBridgeMaterials;
import com.zxcmc.exort.integration.worldedit.WorldEditBulkConfig;
import com.zxcmc.exort.integration.worldedit.WorldEditIntegration;
import com.zxcmc.exort.integration.worldedit.WorldEditRuntimeBootstrap;
import com.zxcmc.exort.integration.worldedit.wand.KnownWorldEditWandGuard;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.placement.PlacementGuardConfig;
import com.zxcmc.exort.recipes.CraftingRulesConfig;
import com.zxcmc.exort.relay.RelaySetupTracker;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ExortRuntimeFactory {
  private static final String VANILLA_NAMESPACE = "minecraft";

  private ExortRuntimeFactory() {}

  public static ExortRuntimeServices create(
      ExortRuntimeFactoryDependencies deps, boolean refreshItemDictionaries) {
    deps.reloadDefaultSortMode().run();
    CompletableFuture<ItemNameService.Status> langFuture =
        reloadLanguage(deps, refreshItemDictionaries);
    CreativeTabOrder.init(deps.plugin());

    RuntimeItemModelConfig itemModels =
        RuntimeItemModelConfig.forMode(deps.resourceMode(), deps.resourceWireUsesBarrier());
    RuntimeMaterials materials =
        new RuntimeMaterials(
            itemModels.wireMaterial(),
            itemModels.storageCarrier(),
            itemModels.terminalCarrier(),
            itemModels.monitorCarrier(),
            itemModels.busCarrier(),
            itemModels.relayCarrier(),
            itemModels.transmitterCarrier(),
            itemModels.chunkLoaderCarrier());

    loadStorageTiersConfig(deps);
    RuntimeNetworkConfig networkConfig = RuntimeNetworkConfig.fromConfig(deps.config());
    if (networkConfig.wireHardCapAdjusted()) {
      ExortLog.warn(
          "wireHardCap is below wireLimit; value will be adjusted to " + networkConfig.wireLimit());
    }
    if (deps.networkGraphCache().get() != null) {
      deps.networkGraphCache().get().invalidateAll();
    }
    var relayTraversalCarrier = networkConfig.relayEnabled() ? materials.relayCarrier() : null;

    CustomItems customItems = createCustomItems(deps, itemModels);
    WirelessTerminalService wirelessService = createWirelessService(deps, customItems);
    WirelessTransmitterService wirelessTransmitterService =
        createWirelessTransmitterService(deps, materials, networkConfig);
    TransmitterSessionManager transmitterSessionManager =
        new TransmitterSessionManager(
            deps.plugin(),
            wirelessTransmitterService,
            wirelessService,
            deps.lang(),
            deps.playerFeedback(),
            deps.regionProtection().get(),
            () -> deps.resourceMode(),
            deps.guiOverlayConfig());
    transmitterSessionManager.reconfigure();
    wirelessTransmitterService.scanLoadedChunks();
    deps.sessionManager().reconfigure();

    deps.stopReloadableRuntime().run();
    PacketEnhancements packetEnhancements =
        PacketEnhancementsFactory.tryCreate(deps.plugin(), deps.pickDebugFullSink());
    ChunkLoaderConfig chunkLoaderConfig =
        ChunkLoaderConfig.fromConfig(deps.config(), deps.plugin().getLogger());
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
    chunkLoaderService.start();
    BreakAnimationSender breakAnimationSender =
        RuntimeBreakAnimationSenders.create(
            deps.plugin(), deps.resourceMode(), itemModels.displayNamespace(), materials);
    deps.unregisterReloadableRuntimeListeners().run();
    deps.setupRegionProtection().run();
    deps.resetReloadableDisplayState().run();
    AuthenticationGate authenticationGate = new KnownAuthenticationGate(deps.plugin());
    WorldEditWandGuard worldEditWandGuard = new KnownWorldEditWandGuard(deps.plugin());

    RuntimeServiceState state = new RuntimeServiceState();
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
    RuntimeHologramConfig hologramConfig = RuntimeHologramConfig.forMode(deps.resourceMode());
    RuntimeDisplayServices displayServices =
        RuntimeDisplayServicesFactory.create(
            new RuntimeDisplayServicesDependencies(
                deps.plugin(),
                deps.config(),
                deps.lang(),
                deps.keys(),
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
                () -> {
                  if (deps.networkGraphCache().get() != null) {
                    deps.networkGraphCache().get().invalidateAll();
                  }
                }));
    state.displayRefreshService = displayServices.displayRefreshService();

    BusRuntimeConfig busRuntime = BusRuntimeConfig.fromConfig(deps.config());
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
                deps.renderStorage()));
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
                deps.sessionManager(),
                () -> displayServices.monitorDisplayManager(),
                () -> busServices.busSessionManager(),
                () -> busServices.busService(),
                deps.networkGraphCache(),
                deps.regionProtection().get(),
                worldEditWandGuard,
                deps.playerFeedback(),
                breakAnimationSender,
                wirelessTransmitterService,
                transmitterSessionManager,
                chunkLoaderService,
                packetEnhancements));

    boolean dialogSupported = detectDialogSupport(deps);
    RuntimeListenerRegistration listenerRegistration =
        RuntimeListenerRegistrar.register(
            new RuntimeListenerDependencies(
                deps.plugin(),
                deps.database(),
                deps.storageManager(),
                deps.sessionManager(),
                deps.keys(),
                customItems,
                wirelessService,
                wirelessTransmitterService,
                transmitterSessionManager,
                deps.regionProtection().get(),
                authenticationGate,
                worldEditWandGuard,
                deps.playerFeedback(),
                deps.bossBarManager(),
                deps.searchDialogService(),
                deps.lang(),
                deps.itemNameService(),
                deps.inventoryRefreshService(),
                materials,
                relaySetupTracker,
                networkConfig.relayEnabled(),
                networkConfig.wireLimit(),
                networkConfig.wireHardCap(),
                relayTraversalCarrier,
                networkConfig.relayRangeChunks(),
                displayServices.hologramManager(),
                () -> displayServices.hologramManager(),
                displayServices.wireDisplayManager(),
                displayServices.terminalDisplayManager(),
                displayServices.monitorDisplayManager(),
                displayServices.busDisplayManager(),
                displayServices.displayRefreshService(),
                busServices.busService(),
                busServices.busSessionManager(),
                breakingServices.customBlockBreaker(),
                breakingServices.breakHandler(),
                breakingServices.breakSoundConfig(),
                chunkLoaderService,
                packetEnhancements,
                deps.previousRecipeService(),
                busRuntime,
                CraftingRulesConfig.defaults(),
                networkConfig.storagePeekTicks(),
                networkConfig.wirePeekTicks(),
                deps.revalidateSessions(),
                deps.pickDebugSink(),
                deps.monitorPlacedRecorder(),
                deps.monitorRecentlyPlaced()),
            PlacementGuardConfig.fromConfig(deps.config()));

    RuntimePostRefreshScheduler.schedule(
        new RuntimePostRefreshScheduler.RuntimePostRefreshDependencies(
            deps.plugin(),
            deps.networkGraphCache(),
            displayServices.displayRefreshService(),
            deps.storageManager(),
            deps.inventoryRefreshService()));
    WorldEditBridgeDependencies worldEditDependencies =
        new WorldEditBridgeDependencies(
            deps.plugin(),
            deps.database(),
            deps.storageManager(),
            deps.worldEditDebugService(),
            deps.networkGraphCache(),
            displayServices::displayRefreshService,
            busServices::busService,
            () -> chunkLoaderService,
            () -> wirelessTransmitterService,
            () -> transmitterSessionManager,
            displayServices::hologramManager,
            new WorldEditBridgeMaterials(
                materials.wire(),
                materials.storageCarrier(),
                materials.terminalCarrier(),
                materials.monitorCarrier(),
                materials.busCarrier(),
                materials.relayCarrier(),
                materials.transmitterCarrier(),
                materials.chunkLoaderCarrier()),
            WorldEditBulkConfig.fromConfig(deps.config()));
    WorldEditIntegration worldEditIntegration =
        WorldEditRuntimeBootstrap.register(
            worldEditDependencies, deps.tryRegisterWorldEdit(), deps.worldEditIntegrationSink());
    if (deps.runtimeTasks() != null) {
      deps.runtimeTasks().schedule();
    }

    return new ExortRuntimeServices(
        langFuture,
        customItems,
        wirelessService,
        wirelessTransmitterService,
        transmitterSessionManager,
        chunkLoaderService,
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
        worldEditIntegration,
        dialogSupported);
  }

  private static CompletableFuture<ItemNameService.Status> reloadLanguage(
      ExortRuntimeFactoryDependencies deps, boolean refreshItemDictionaries) {
    String langCode = deps.config().getString("language", "en_us");
    String normalized = deps.itemNameService().normalizeLanguage(langCode);
    Lang lang = deps.lang();
    lang.reload(normalized);
    deps.searchDialogService().invalidate();
    CompletableFuture<ItemNameService.Status> langFuture =
        refreshItemDictionaries
            ? deps.itemNameService().refresh(normalized)
            : deps.itemNameService().reload(normalized);
    return langFuture;
  }

  private static void loadStorageTiersConfig(ExortRuntimeFactoryDependencies deps) {
    File tiersFile = new File(deps.plugin().getDataFolder(), "storage-tiers.yml");
    YamlConfiguration cfg = new YamlConfiguration();
    if (!tiersFile.exists()) {
      StorageTier.loadFromConfig(cfg.getConfigurationSection("tiers"), deps.plugin().getLogger());
      return;
    }
    try {
      cfg.load(tiersFile);
    } catch (Exception e) {
      ExortLog.warn("Failed to load storage-tiers.yml: " + e.getMessage());
    }
    StorageTier.loadFromConfig(cfg.getConfigurationSection("tiers"), deps.plugin().getLogger());
  }

  private static CustomItems createCustomItems(
      ExortRuntimeFactoryDependencies deps, RuntimeItemModelConfig itemModels) {
    return new CustomItems(
        deps.keys(),
        deps.lang(),
        itemModels.wireItemModel(),
        itemModels.storageItemModel(),
        itemModels.terminalItemModel(),
        itemModels.craftingTerminalItemModel(),
        itemModels.monitorItemModel(),
        itemModels.importBusItemModel(),
        itemModels.exportBusItemModel(),
        itemModels.relayItemModel(),
        itemModels.transmitterItemModel(),
        itemModels.chunkLoaderItemModel(),
        itemModels.personalChunkLoaderItemModel(),
        itemModels.dormantChunkLoaderItemModel(),
        itemModels.wirelessItemModel(),
        itemModels.wirelessDisabledModel(),
        VANILLA_NAMESPACE + ":target",
        deps.resourceMode());
  }

  private static WirelessTerminalService createWirelessService(
      ExortRuntimeFactoryDependencies deps, CustomItems customItems) {
    WirelessRuntimeConfig wirelessConfig = WirelessRuntimeConfig.fromConfig(deps.config());
    return new WirelessTerminalService(
        deps.lang(),
        deps.keys(),
        customItems,
        wirelessConfig.enabled(),
        wirelessConfig.rangeBlocks());
  }

  private static WirelessTransmitterService createWirelessTransmitterService(
      ExortRuntimeFactoryDependencies deps,
      RuntimeMaterials materials,
      RuntimeNetworkConfig networkConfig) {
    WirelessRuntimeConfig wirelessConfig = WirelessRuntimeConfig.fromConfig(deps.config());
    return new WirelessTransmitterService(
        deps.plugin(),
        deps.keys(),
        wirelessConfig.enabled(),
        wirelessConfig.rangeBlocks(),
        networkConfig.wireLimit(),
        networkConfig.wireHardCap(),
        networkConfig.relayRangeChunks(),
        materials.wire(),
        materials.storageCarrier(),
        materials.transmitterCarrier(),
        networkConfig.relayEnabled() ? materials.relayCarrier() : null,
        deps.networkGraphCache());
  }

  private static boolean detectDialogSupport(ExortRuntimeFactoryDependencies deps) {
    try {
      Class.forName(
          "io.papermc.paper.dialog.Dialog", false, deps.plugin().getClass().getClassLoader());
      Class.forName(
          "io.papermc.paper.event.player.PlayerCustomClickEvent",
          false,
          deps.plugin().getClass().getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static final class RuntimeServiceState {
    private BusService busService;
    private DisplayRefreshService displayRefreshService;
  }
}
