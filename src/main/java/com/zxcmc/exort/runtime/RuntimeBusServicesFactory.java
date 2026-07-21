package com.zxcmc.exort.runtime;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionDependencies;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.bus.engine.BusEngineDependencies;
import com.zxcmc.exort.carrier.CarrierMaterials;

public final class RuntimeBusServicesFactory {
  private RuntimeBusServicesFactory() {}

  public static RuntimeBusServices create(
      RuntimeBusServicesDependencies deps,
      RuntimeGenerationScope generation,
      RuntimeHandle.Scope resources) {
    CarrierMaterials materials = deps.materials();
    var busDependencies =
        new BusEngineDependencies(
            deps.plugin(),
            deps.keys(),
            deps.wireLimit(),
            deps.wireHardCap(),
            deps.relayRangeChunks(),
            materials.wire(),
            materials.storageCarrier(),
            deps.relayTraversalCarrier(),
            deps.networkGraphCache(),
            deps.renderStorage());
    BusService busService =
        new BusService(
            busDependencies,
            deps.storageManager(),
            deps.database(),
            materials.busCarrier(),
            deps.busRuntime(),
            deps.wirelessService());
    resources.own("bus service", busService::stop);
    busService.start();
    generation.runTask(busService::scanLoadedChunks);

    var busSessionDependencies =
        new BusSessionDependencies(
            deps.plugin(),
            deps.keys(),
            deps.bossBarManager(),
            deps.resourceMode(),
            materials::busCarrier,
            deps.wireLimit(),
            deps.wireHardCap(),
            deps.relayRangeChunks(),
            materials.wire(),
            materials.storageCarrier(),
            deps.relayTraversalCarrier(),
            deps.networkGraphCache(),
            deps.guiRuntimeConfig(),
            deps.guiOverlayConfig(),
            deps.itemNameService());
    BusSessionManager busSessionManager =
        new BusSessionManager(busSessionDependencies, busService, deps.lang());
    resources.own("bus sessions", busSessionManager::shutdown);
    busSessionManager.reconfigure();
    return new RuntimeBusServices(busService, busSessionManager);
  }
}
