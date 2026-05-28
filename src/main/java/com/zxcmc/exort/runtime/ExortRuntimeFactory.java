package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BreakAnimationSender;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.gui.CreativeTabOrder;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.protocol.ProtocolLibEnhancements;
import com.zxcmc.exort.integration.worldedit.WorldEditBridgeDependencies;
import com.zxcmc.exort.integration.worldedit.WorldEditBridgeMaterials;
import com.zxcmc.exort.integration.worldedit.WorldEditIntegration;
import com.zxcmc.exort.integration.worldedit.WorldEditRuntimeBootstrap;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.placement.PlacementGuardConfig;
import com.zxcmc.exort.recipes.CraftingRulesConfig;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessTerminalService;
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
        RuntimeItemModelConfig.fromConfig(deps.config(), deps.resourceMode());
    RuntimeMaterials materials =
        new RuntimeMaterials(
            itemModels.wireMaterial(),
            itemModels.storageCarrier(),
            itemModels.terminalCarrier(),
            itemModels.monitorCarrier(),
            itemModels.busCarrier());

    loadStorageTiersConfig(deps);
    RuntimeNetworkConfig networkConfig = RuntimeNetworkConfig.fromConfig(deps.config());
    if (networkConfig.wireHardCapAdjusted()) {
      ExortLog.warn(
          "wireHardCap is below wireLimit; value will be adjusted to " + networkConfig.wireLimit());
    }
    if (deps.networkGraphCache().get() != null) {
      deps.networkGraphCache().get().invalidateAll();
    }

    CustomItems customItems = createCustomItems(deps, itemModels);
    WirelessTerminalService wirelessService = createWirelessService(deps, customItems);
    deps.sessionManager().reconfigure();

    deps.stopReloadableRuntime().run();
    ProtocolLibEnhancements protocolLibEnhancements =
        ProtocolLibEnhancements.tryCreate(deps.plugin(), deps.pickDebugFullSink());
    BreakAnimationSender breakAnimationSender =
        RuntimeBreakAnimationSenders.create(
            deps.plugin(), deps.config(), deps.resourceMode(), itemModels.displayNamespace());
    deps.unregisterReloadableRuntimeListeners().run();
    deps.setupRegionProtection().run();
    deps.resetReloadableDisplayState().run();

    RuntimeServiceState state = new RuntimeServiceState();
    RuntimeHologramConfig hologramConfig =
        RuntimeHologramConfig.fromConfig(deps.config(), deps.resourceMode());
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
                networkConfig.wireLimit(),
                networkConfig.wireHardCap(),
                RuntimeMaterialResolver::resolve,
                deps.worldEditDebugService(),
                () -> state.busService,
                () -> {
                  if (deps.networkGraphCache().get() != null) {
                    deps.networkGraphCache().get().invalidateAll();
                  }
                }));

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
                materials,
                networkConfig.wireLimit(),
                networkConfig.wireHardCap(),
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
                deps.playerFeedback(),
                breakAnimationSender));

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
                deps.regionProtection().get(),
                deps.playerFeedback(),
                deps.bossBarManager(),
                deps.searchDialogService(),
                deps.itemNameService(),
                deps.inventoryRefreshService(),
                materials,
                networkConfig.wireLimit(),
                networkConfig.wireHardCap(),
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
                protocolLibEnhancements,
                deps.previousRecipeService(),
                busRuntime,
                CraftingRulesConfig.fromConfig(deps.config()),
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
            displayServices::hologramManager,
            new WorldEditBridgeMaterials(
                materials.wire(),
                materials.storageCarrier(),
                materials.terminalCarrier(),
                materials.monitorCarrier(),
                materials.busCarrier()));
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
        materials,
        networkConfig.wireLimit(),
        networkConfig.wireHardCap(),
        networkConfig.storagePeekTicks(),
        networkConfig.wirePeekTicks(),
        displayServices.hologramManager(),
        displayServices.wireDisplayManager(),
        displayServices.storageDisplayManager(),
        displayServices.terminalDisplayManager(),
        displayServices.monitorDisplayManager(),
        displayServices.busDisplayManager(),
        displayServices.displayRefreshService(),
        busServices.busService(),
        busServices.busSessionManager(),
        breakingServices.breakHandler(),
        breakingServices.customBlockBreaker(),
        breakingServices.breakSoundConfig(),
        listenerRegistration.craftingRules(),
        listenerRegistration.recipeService(),
        protocolLibEnhancements,
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
    langFuture.thenAccept(
        status -> {
          if (!status.activeLanguage().equalsIgnoreCase(normalized)) {
            lang.reload(status.activeLanguage());
          }
        });
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
        itemModels.wirelessItemModel(),
        itemModels.wirelessDisabledModel(),
        VANILLA_NAMESPACE + ":target");
  }

  private static WirelessTerminalService createWirelessService(
      ExortRuntimeFactoryDependencies deps, CustomItems customItems) {
    WirelessRuntimeConfig wirelessConfig = WirelessRuntimeConfig.fromConfig(deps.config());
    return new WirelessTerminalService(
        deps.lang(),
        deps.keys(),
        customItems,
        wirelessConfig.enabled(),
        wirelessConfig.rangeChunks());
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
  }
}
