package com.zxcmc.exort.runtime;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.WorldEditBridgeDependencies;
import com.zxcmc.exort.integration.worldedit.WorldEditIntegration;
import com.zxcmc.exort.network.NetworkGraphCache;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Optional integrations and generation hand-off state. */
public record RuntimeIntegrationContext(
    Supplier<NetworkGraphCache> networkGraphCache,
    Supplier<RegionProtection> regionProtection,
    Supplier<WorldEditDebugService> worldEditDebugService,
    Supplier<BusService> busService,
    Function<WorldEditBridgeDependencies, WorldEditIntegration> tryRegisterWorldEdit,
    Consumer<WorldEditIntegration> worldEditIntegrationSink) {
  public RuntimeIntegrationContext {
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(worldEditDebugService, "worldEditDebugService");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(tryRegisterWorldEdit, "tryRegisterWorldEdit");
    Objects.requireNonNull(worldEditIntegrationSink, "worldEditIntegrationSink");
  }
}
