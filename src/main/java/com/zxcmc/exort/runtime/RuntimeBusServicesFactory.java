package com.zxcmc.exort.runtime;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionDependencies;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.bus.engine.BusEngineDependencies;
import org.bukkit.Bukkit;

public final class RuntimeBusServicesFactory {
  private RuntimeBusServicesFactory() {}

  public static RuntimeBusServices create(RuntimeBusServicesDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    var busDependencies =
        new BusEngineDependencies(
            deps.plugin(),
            deps.keys(),
            deps.wireLimit(),
            deps.wireHardCap(),
            materials.wire(),
            materials.storageCarrier(),
            deps.renderStorage());
    BusService busService =
        new BusService(
            busDependencies,
            deps.storageManager(),
            deps.database(),
            materials.busCarrier(),
            deps.busRuntime(),
            deps.wirelessService());
    busService.start();
    Bukkit.getScheduler().runTask(deps.plugin(), busService::scanLoadedChunks);

    var busSessionDependencies =
        new BusSessionDependencies(
            deps.plugin(),
            deps.keys(),
            deps.bossBarManager(),
            deps.resourceMode(),
            materials::busCarrier,
            deps.wireLimit(),
            deps.wireHardCap(),
            materials.wire(),
            materials.storageCarrier(),
            deps.guiRuntimeConfig(),
            deps.guiOverlayConfig());
    BusSessionManager busSessionManager =
        new BusSessionManager(busSessionDependencies, busService, deps.lang());
    busSessionManager.reconfigure();
    return new RuntimeBusServices(busService, busSessionManager);
  }
}
