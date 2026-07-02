package com.zxcmc.exort.runtime;

import com.zxcmc.exort.display.core.DisplayEntityIndex;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.display.culling.DisplayCullingConfig;
import com.zxcmc.exort.display.culling.DisplayCullingService;
import com.zxcmc.exort.display.device.BusDisplayManager;
import com.zxcmc.exort.display.device.ChunkLoaderDisplayManager;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.device.RelayDisplayManager;
import com.zxcmc.exort.display.device.StorageDisplayManager;
import com.zxcmc.exort.display.device.TerminalDisplayManager;
import com.zxcmc.exort.display.localization.DisplayLocalizationRefreshService;
import com.zxcmc.exort.display.localization.ExortDisplayLocalizationService;
import com.zxcmc.exort.display.proxy.ExortBlockProxyService;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.i18n.ExortItemLocalizationService;
import com.zxcmc.exort.integration.protocol.PacketLocalizationLevel;
import com.zxcmc.exort.relay.RelayVisualStateResolver;
import com.zxcmc.exort.sanity.ChunkSanityService;
import com.zxcmc.exort.sanity.DisplayCleanupService;
import com.zxcmc.exort.sanity.MarkerSanityDependencies;
import com.zxcmc.exort.sanity.MarkerSanityService;
import com.zxcmc.exort.sanity.listener.ChunkSanityListener;
import org.bukkit.Bukkit;

public final class RuntimeDisplayServicesFactory {
  private RuntimeDisplayServicesFactory() {}

  public static RuntimeDisplayServices create(RuntimeDisplayServicesDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    RuntimeItemModelConfig itemModels = deps.itemModels();
    RuntimeDisplayModelConfig displayModels =
        RuntimeDisplayModelConfig.forMode(deps.resourceMode(), itemModels.displayNamespace());
    DisplayCullingConfig displayCullingConfig = DisplayCullingConfig.fromConfig(deps.config());
    DisplayEntityIndex displayEntityIndex = new DisplayEntityIndex();
    DisplayMetadataService metadataService =
        new DisplayMetadataService(displayEntityIndex, displayCullingConfig);
    boolean fullPacketLocalization = registerPacketLocalization(deps, displayEntityIndex);
    if (fullPacketLocalization) {
      Bukkit.getPluginManager()
          .registerEvents(
              new DisplayLocalizationRefreshService(
                  deps.plugin(),
                  displayEntityIndex,
                  metadataService,
                  displayCullingConfig.maxDistance()),
              deps.plugin());
    }

    ItemHologramManager hologramManager =
        new ItemHologramManager(
            deps.plugin(),
            deps.keys(),
            deps.wireLimit(),
            deps.wireHardCap(),
            deps.relayRangeChunks(),
            materials.wire(),
            materials.storageCarrier(),
            materials.relayCarrier(),
            materials.terminalCarrier(),
            deps.hologramConfig().terminal(),
            deps.hologramConfig().storage(),
            metadataService);
    hologramManager.start();
    Bukkit.getPluginManager().registerEvents(hologramManager, deps.plugin());

    WireDisplayManager wireDisplayManager = createWireDisplayManager(deps, metadataService);
    if (wireDisplayManager.isEnabled()) {
      Bukkit.getScheduler().runTask(deps.plugin(), wireDisplayManager::scanLoadedChunks);
    }

    StorageDisplayManager storageDisplayManager =
        createStorageDisplayManager(deps, displayModels, metadataService);
    Bukkit.getScheduler().runTask(deps.plugin(), storageDisplayManager::scanLoadedChunks);

    TerminalDisplayManager terminalDisplayManager =
        createTerminalDisplayManager(deps, displayModels, metadataService);
    Bukkit.getScheduler().runTask(deps.plugin(), terminalDisplayManager::scanLoadedChunks);

    MonitorDisplayManager monitorDisplayManager =
        createMonitorDisplayManager(deps, displayModels, metadataService);
    Bukkit.getScheduler().runTask(deps.plugin(), monitorDisplayManager::start);

    BusDisplayManager busDisplayManager =
        createBusDisplayManager(deps, displayModels, metadataService);
    Bukkit.getScheduler().runTask(deps.plugin(), busDisplayManager::scanLoadedChunks);

    RelayDisplayManager relayDisplayManager =
        createRelayDisplayManager(deps, displayModels, metadataService);
    Bukkit.getScheduler().runTask(deps.plugin(), relayDisplayManager::scanLoadedChunks);

    ChunkLoaderDisplayManager chunkLoaderDisplayManager =
        createChunkLoaderDisplayManager(deps, displayModels, metadataService);
    Bukkit.getScheduler().runTask(deps.plugin(), chunkLoaderDisplayManager::scanLoadedChunks);
    Bukkit.getScheduler().runTask(deps.plugin(), metadataService::rebuildLoadedDisplays);

    ExortBlockProxyService blockProxyService =
        new ExortBlockProxyService(
            deps.plugin(),
            displayCullingConfig.blockProxy(),
            deps.resourceMode() && displayCullingConfig.enabled(),
            materials.storageCarrier(),
            materials.terminalCarrier(),
            materials.monitorCarrier(),
            materials.busCarrier(),
            materials.relayCarrier(),
            materials.chunkLoaderCarrier());
    blockProxyService.start();

    DisplayCullingService displayCullingService =
        new DisplayCullingService(
            deps.plugin(),
            displayCullingConfig,
            deps.packetEnhancements(),
            displayEntityIndex,
            metadataService,
            blockProxyService,
            deps.database());
    displayCullingService.start();

    DisplayRefreshService displayRefreshService =
        new DisplayRefreshService(
            deps.plugin(),
            deps.wireHardCap(),
            deps.relayRangeChunks(),
            materials.wire(),
            materials.terminalCarrier(),
            materials.monitorCarrier(),
            materials.busCarrier(),
            materials.relayCarrier(),
            materials.chunkLoaderCarrier(),
            materials.storageCarrier(),
            wireDisplayManager,
            storageDisplayManager,
            terminalDisplayManager,
            monitorDisplayManager,
            busDisplayManager,
            relayDisplayManager,
            chunkLoaderDisplayManager,
            blockProxyService,
            deps.relaySetupTracker());
    registerSanityServices(deps, hologramManager, displayRefreshService);

    return new RuntimeDisplayServices(
        hologramManager,
        wireDisplayManager,
        storageDisplayManager,
        terminalDisplayManager,
        monitorDisplayManager,
        busDisplayManager,
        relayDisplayManager,
        chunkLoaderDisplayManager,
        blockProxyService,
        displayCullingService,
        displayRefreshService);
  }

  private static WireDisplayManager createWireDisplayManager(
      RuntimeDisplayServicesDependencies deps, DisplayMetadataService metadataService) {
    RuntimeDisplayConfig wireDisplay = RuntimeDisplayConfig.defaults();
    RuntimeMaterials materials = deps.materials();
    return new WireDisplayManager(
        deps.plugin(),
        true,
        materials.wire(),
        materials.terminalCarrier(),
        materials.storageCarrier(),
        materials.monitorCarrier(),
        materials.busCarrier(),
        materials.relayCarrier(),
        deps.itemModels().displayNamespace(),
        deps.itemModels().wireItemModel(),
        deps.resourceMode(),
        wireDisplay.displayBaseMaterial(),
        wireDisplay.displayScale(),
        wireDisplay.offsetX(),
        wireDisplay.offsetY(),
        wireDisplay.offsetZ(),
        deps.lang().clientComponent(deps.resourceMode(), "item.wire"),
        metadataService);
  }

  private static boolean registerPacketLocalization(
      RuntimeDisplayServicesDependencies deps, DisplayEntityIndex displayEntityIndex) {
    if (deps.packetEnhancements() == null) {
      return false;
    }
    PacketLocalizationLevel level = PacketLocalizationLevel.fromConfig(deps.config());
    ExortDisplayLocalizationService displayLocalization =
        new ExortDisplayLocalizationService(displayEntityIndex, deps.lang());
    return deps.packetEnhancements()
        .registerLocalization(
            new ExortItemLocalizationService(deps.keys(), deps.lang())::localize,
            displayLocalization::localize,
            deps.resourceMode(),
            level);
  }

  private static StorageDisplayManager createStorageDisplayManager(
      RuntimeDisplayServicesDependencies deps,
      RuntimeDisplayModelConfig displayModels,
      DisplayMetadataService metadataService) {
    RuntimeDisplayConfig storageDisplay = RuntimeDisplayConfig.defaults();
    return new StorageDisplayManager(
        deps.plugin(),
        deps.materials().storageCarrier(),
        displayModels.storage(),
        storageDisplay.displayBaseMaterial(),
        storageDisplay.displayScale(),
        storageDisplay.offsetX(),
        storageDisplay.offsetY(),
        storageDisplay.offsetZ(),
        metadataService,
        deps.lang().clientComponent(deps.resourceMode(), "item.storage"),
        deps.lang().clientComponent(deps.resourceMode(), "item.storage_core"));
  }

  private static TerminalDisplayManager createTerminalDisplayManager(
      RuntimeDisplayServicesDependencies deps,
      RuntimeDisplayModelConfig displayModels,
      DisplayMetadataService metadataService) {
    RuntimeDisplayConfig terminalDisplay = RuntimeDisplayConfig.defaults();
    RuntimeMaterials materials = deps.materials();
    return new TerminalDisplayManager(
        deps.plugin(),
        materials.terminalCarrier(),
        displayModels.terminal(),
        displayModels.terminalDisabled(),
        displayModels.craftingTerminal(),
        displayModels.craftingTerminalDisabled(),
        terminalDisplay.displayBaseMaterial(),
        terminalDisplay.displayScale(),
        terminalDisplay.offsetX(),
        terminalDisplay.offsetY(),
        terminalDisplay.offsetZ(),
        metadataService,
        deps.lang().clientComponent(deps.resourceMode(), "item.terminal"),
        deps.lang().clientComponent(deps.resourceMode(), "item.crafting_terminal"),
        deps.keys(),
        deps.wireLimit(),
        deps.wireHardCap(),
        deps.relayRangeChunks(),
        materials.wire(),
        materials.storageCarrier(),
        materials.relayCarrier(),
        deps.resourceMode());
  }

  private static MonitorDisplayManager createMonitorDisplayManager(
      RuntimeDisplayServicesDependencies deps,
      RuntimeDisplayModelConfig displayModels,
      DisplayMetadataService metadataService) {
    RuntimeDisplayConfig monitorDisplay = RuntimeDisplayConfig.defaults();
    RuntimeMonitorScreenConfig monitorScreens =
        RuntimeMonitorScreenConfig.forMode(deps.resourceMode());
    RuntimeMaterials materials = deps.materials();
    return new MonitorDisplayManager(
        deps.plugin(),
        deps.keys(),
        deps.storageManager(),
        materials.monitorCarrier(),
        displayModels.monitor(),
        displayModels.monitorDisabled(),
        monitorDisplay.displayBaseMaterial(),
        monitorDisplay.displayScale(),
        monitorDisplay.offsetX(),
        monitorDisplay.offsetY(),
        monitorDisplay.offsetZ(),
        metadataService,
        deps.lang().clientComponent(deps.resourceMode(), "item.monitor"),
        deps.wireLimit(),
        deps.wireHardCap(),
        deps.relayRangeChunks(),
        materials.wire(),
        materials.storageCarrier(),
        materials.relayCarrier(),
        monitorScreens.item(),
        monitorScreens.block(),
        monitorScreens.thinBlock(),
        monitorScreens.horizontalBlock(),
        monitorScreens.fullBlock(),
        monitorScreens.text(),
        monitorScreens.textEmpty(),
        monitorScreens.textBackgroundAlpha());
  }

  private static BusDisplayManager createBusDisplayManager(
      RuntimeDisplayServicesDependencies deps,
      RuntimeDisplayModelConfig displayModels,
      DisplayMetadataService metadataService) {
    RuntimeDisplayConfig busDisplay = RuntimeDisplayConfig.defaults();
    return new BusDisplayManager(
        deps.plugin(),
        deps.materials().busCarrier(),
        displayModels.importBus(),
        displayModels.exportBus(),
        busDisplay.displayBaseMaterial(),
        busDisplay.displayScale(),
        busDisplay.offsetX(),
        busDisplay.offsetY(),
        busDisplay.offsetZ(),
        metadataService,
        deps.lang().clientComponent(deps.resourceMode(), "item.import_bus"),
        deps.lang().clientComponent(deps.resourceMode(), "item.export_bus"));
  }

  private static RelayDisplayManager createRelayDisplayManager(
      RuntimeDisplayServicesDependencies deps,
      RuntimeDisplayModelConfig displayModels,
      DisplayMetadataService metadataService) {
    RuntimeDisplayConfig relayDisplay = RuntimeDisplayConfig.defaults();
    RuntimeMaterials materials = deps.materials();
    RelayVisualStateResolver visualStateResolver =
        deps.resourceMode()
            ? new RelayVisualStateResolver(
                deps.plugin(),
                deps.keys(),
                deps.relaySetupTracker(),
                deps.wireLimit(),
                deps.wireHardCap(),
                deps.relayRangeChunks(),
                materials.wire(),
                materials.storageCarrier(),
                materials.relayCarrier())
            : null;
    return new RelayDisplayManager(
        deps.plugin(),
        materials.relayCarrier(),
        displayModels.relay(),
        displayModels.relayGreen(),
        displayModels.relayBlue(),
        displayModels.relayRed(),
        relayDisplay.displayBaseMaterial(),
        relayDisplay.displayScale(),
        relayDisplay.offsetX(),
        relayDisplay.offsetY(),
        relayDisplay.offsetZ(),
        metadataService,
        deps.lang().clientComponent(deps.resourceMode(), "item.relay"),
        visualStateResolver);
  }

  private static ChunkLoaderDisplayManager createChunkLoaderDisplayManager(
      RuntimeDisplayServicesDependencies deps,
      RuntimeDisplayModelConfig displayModels,
      DisplayMetadataService metadataService) {
    RuntimeDisplayConfig display = RuntimeDisplayConfig.defaults();
    return new ChunkLoaderDisplayManager(
        deps.plugin(),
        deps.materials().chunkLoaderCarrier(),
        displayModels.chunkLoader(),
        display.displayBaseMaterial(),
        display.displayScale(),
        display.offsetX(),
        display.offsetY(),
        display.offsetZ(),
        metadataService,
        deps.lang().clientComponent(deps.resourceMode(), "item.chunk_loader"),
        deps.lang().clientComponent(deps.resourceMode(), "item.personal_chunk_loader"),
        deps.lang().clientComponent(deps.resourceMode(), "item.dormant_chunk_loader"));
  }

  private static void registerSanityServices(
      RuntimeDisplayServicesDependencies deps,
      ItemHologramManager hologramManager,
      DisplayRefreshService displayRefreshService) {
    RuntimeMaterials materials = deps.materials();
    var chunkSanityService =
        new ChunkSanityService(
            deps.plugin(),
            new DisplayCleanupService(
                deps.plugin(),
                materials.wire(),
                materials.storageCarrier(),
                materials.terminalCarrier(),
                materials.monitorCarrier(),
                materials.busCarrier(),
                materials.relayCarrier(),
                materials.chunkLoaderCarrier()),
            new MarkerSanityService(
                new MarkerSanityDependencies(
                    deps.plugin(),
                    displayRefreshService,
                    () -> hologramManager,
                    deps.busService(),
                    deps.database(),
                    materials.wire(),
                    materials.storageCarrier(),
                    materials.terminalCarrier(),
                    materials.monitorCarrier(),
                    materials.busCarrier(),
                    materials.relayCarrier(),
                    materials.chunkLoaderCarrier())),
            displayRefreshService,
            deps.worldEditDebugService(),
            deps.invalidateNetwork());
    var chunkSanityListener =
        new ChunkSanityListener(deps.plugin(), chunkSanityService, deps.invalidateNetwork());
    Bukkit.getPluginManager().registerEvents(chunkSanityListener, deps.plugin());
    Bukkit.getScheduler().runTask(deps.plugin(), chunkSanityService::scanLoadedChunks);
  }
}
