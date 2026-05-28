package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BlockBreakHandlerDependencies;
import com.zxcmc.exort.breaking.BreakConfig;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.breaking.CustomBlockBreaker;

public final class RuntimeBreakingServicesFactory {
  private RuntimeBreakingServicesFactory() {}

  public static RuntimeBreakingServices create(RuntimeBreakingServicesDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    BlockBreakHandler breakHandler =
        new BlockBreakHandler(
            new BlockBreakHandlerDependencies(
                deps.plugin(),
                deps.customItems(),
                materials.wire(),
                materials.storageCarrier(),
                materials.terminalCarrier(),
                materials.monitorCarrier(),
                materials.busCarrier(),
                deps.hologramManager(),
                deps.wireDisplayManager(),
                deps.displayRefreshService(),
                deps.breakAnimationSender(),
                deps.storageManager(),
                deps.sessionManager(),
                deps.monitorDisplayManager(),
                deps.busSessionManager(),
                deps.busService(),
                deps.networkGraphCache(),
                deps.regionProtection(),
                deps.playerFeedback()));
    BreakConfig breakConfig = BreakConfig.fromConfig(deps.config(), deps.logger());
    BreakSoundConfig breakSoundConfig = BreakSoundConfig.fromConfig(deps.config());
    CustomBlockBreaker customBlockBreaker =
        new CustomBlockBreaker(
            deps.plugin(),
            deps.regionProtection(),
            breakHandler,
            breakConfig,
            breakSoundConfig,
            deps.breakAnimationSender(),
            materials.wire(),
            materials.storageCarrier(),
            materials.terminalCarrier(),
            materials.monitorCarrier(),
            materials.busCarrier());
    return new RuntimeBreakingServices(breakHandler, breakSoundConfig, customBlockBreaker);
  }
}
