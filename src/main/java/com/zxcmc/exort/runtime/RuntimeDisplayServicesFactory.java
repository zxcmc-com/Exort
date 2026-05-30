package com.zxcmc.exort.runtime;

import com.zxcmc.exort.display.BusDisplayManager;
import com.zxcmc.exort.display.DisplayCullingConfig;
import com.zxcmc.exort.display.DisplayCullingService;
import com.zxcmc.exort.display.DisplayEntityIndex;
import com.zxcmc.exort.display.DisplayMetadataService;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.MonitorDisplayManager;
import com.zxcmc.exort.display.StorageDisplayManager;
import com.zxcmc.exort.display.TerminalDisplayManager;
import com.zxcmc.exort.display.WireDisplayManager;
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
        RuntimeDisplayModelConfig.fromConfig(
            deps.config(), deps.resourceMode(), itemModels.displayNamespace());
    DisplayCullingConfig displayCullingConfig = DisplayCullingConfig.fromConfig(deps.config());
    DisplayEntityIndex displayEntityIndex = new DisplayEntityIndex();
    DisplayMetadataService metadataService =
        new DisplayMetadataService(displayEntityIndex, displayCullingConfig);

    ItemHologramManager hologramManager =
        new ItemHologramManager(
            deps.plugin(),
            deps.keys(),
            deps.wireLimit(),
            deps.wireHardCap(),
            materials.wire(),
            materials.storageCarrier(),
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
    Bukkit.getScheduler().runTask(deps.plugin(), metadataService::rebuildLoadedDisplays);

    DisplayCullingService displayCullingService =
        new DisplayCullingService(
            deps.plugin(),
            displayCullingConfig,
            deps.protocolLibEnhancements(),
            displayEntityIndex,
            metadataService,
            deps.database());
    displayCullingService.start();

    DisplayRefreshService displayRefreshService =
        new DisplayRefreshService(
            deps.plugin(),
            deps.wireHardCap(),
            materials.wire(),
            materials.terminalCarrier(),
            materials.monitorCarrier(),
            materials.busCarrier(),
            materials.storageCarrier(),
            wireDisplayManager,
            storageDisplayManager,
            terminalDisplayManager,
            monitorDisplayManager,
            busDisplayManager);
    registerSanityServices(deps, hologramManager, displayRefreshService);

    return new RuntimeDisplayServices(
        hologramManager,
        wireDisplayManager,
        storageDisplayManager,
        terminalDisplayManager,
        monitorDisplayManager,
        busDisplayManager,
        displayCullingService,
        displayRefreshService);
  }

  private static WireDisplayManager createWireDisplayManager(
      RuntimeDisplayServicesDependencies deps, DisplayMetadataService metadataService) {
    RuntimeDisplayConfig wireDisplay =
        RuntimeDisplayConfig.fromConfig(
            deps.config(), deps.resourceMode(), "resourceMode.wire", deps.materialResolver());
    RuntimeMaterials materials = deps.materials();
    return new WireDisplayManager(
        deps.plugin(),
        true,
        materials.wire(),
        materials.terminalCarrier(),
        materials.storageCarrier(),
        materials.monitorCarrier(),
        materials.busCarrier(),
        deps.itemModels().displayNamespace(),
        deps.itemModels().wireItemModel(),
        deps.resourceMode(),
        wireDisplay.displayBaseMaterial(),
        wireDisplay.displayScale(),
        wireDisplay.offsetX(),
        wireDisplay.offsetY(),
        wireDisplay.offsetZ(),
        deps.lang().tr("item.wire"),
        metadataService);
  }

  private static StorageDisplayManager createStorageDisplayManager(
      RuntimeDisplayServicesDependencies deps,
      RuntimeDisplayModelConfig displayModels,
      DisplayMetadataService metadataService) {
    RuntimeDisplayConfig storageDisplay =
        RuntimeDisplayConfig.fromConfig(
            deps.config(), deps.resourceMode(), "resourceMode.storage", deps.materialResolver());
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
        deps.lang().tr("item.storage_core"));
  }

  private static TerminalDisplayManager createTerminalDisplayManager(
      RuntimeDisplayServicesDependencies deps,
      RuntimeDisplayModelConfig displayModels,
      DisplayMetadataService metadataService) {
    RuntimeDisplayConfig terminalDisplay =
        RuntimeDisplayConfig.fromConfig(
            deps.config(), deps.resourceMode(), "resourceMode.terminal", deps.materialResolver());
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
        deps.lang().tr("item.terminal"),
        deps.lang().tr("item.crafting_terminal"),
        deps.keys(),
        deps.wireLimit(),
        deps.wireHardCap(),
        materials.wire(),
        materials.storageCarrier(),
        deps.resourceMode());
  }

  private static MonitorDisplayManager createMonitorDisplayManager(
      RuntimeDisplayServicesDependencies deps,
      RuntimeDisplayModelConfig displayModels,
      DisplayMetadataService metadataService) {
    RuntimeDisplayConfig monitorDisplay =
        RuntimeDisplayConfig.fromConfig(
            deps.config(), deps.resourceMode(), "resourceMode.monitor", deps.materialResolver());
    RuntimeMonitorScreenConfig monitorScreens =
        RuntimeMonitorScreenConfig.fromConfig(deps.config(), deps.resourceMode());
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
        deps.lang().tr("item.monitor"),
        deps.wireLimit(),
        deps.wireHardCap(),
        materials.wire(),
        materials.storageCarrier(),
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
    RuntimeDisplayConfig busDisplay =
        RuntimeDisplayConfig.fromConfig(
            deps.config(), deps.resourceMode(), "resourceMode.bus", deps.materialResolver());
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
        deps.lang().tr("item.import_bus"),
        deps.lang().tr("item.export_bus"));
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
                materials.busCarrier()),
            new MarkerSanityService(
                new MarkerSanityDependencies(
                    deps.plugin(),
                    displayRefreshService,
                    () -> hologramManager,
                    deps.busService(),
                    deps.storageManager(),
                    deps.database(),
                    materials.wire(),
                    materials.storageCarrier(),
                    materials.terminalCarrier(),
                    materials.monitorCarrier(),
                    materials.busCarrier())),
            displayRefreshService,
            deps.worldEditDebugService(),
            deps.invalidateNetwork());
    var chunkSanityListener =
        new ChunkSanityListener(deps.plugin(), chunkSanityService, deps.invalidateNetwork());
    Bukkit.getPluginManager().registerEvents(chunkSanityListener, deps.plugin());
    Bukkit.getScheduler().runTask(deps.plugin(), chunkSanityService::scanLoadedChunks);
  }
}
