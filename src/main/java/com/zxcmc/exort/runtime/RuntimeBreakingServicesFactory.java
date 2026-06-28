package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BlockBreakHandlerDependencies;
import com.zxcmc.exort.breaking.BreakConfig;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.breaking.CustomBlockBreaker;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;

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
                materials.relayCarrier(),
                materials.chunkLoaderCarrier(),
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
                deps.playerFeedback(),
                deps.chunkLoaderService()));
    BreakConfig breakConfig = BreakConfig.fromConfig(deps.config(), deps.logger());
    BreakSoundConfig breakSoundConfig = BreakSoundConfig.defaults();
    CustomBlockBreaker customBlockBreaker =
        new CustomBlockBreaker(
            deps.plugin(),
            deps.regionProtection(),
            deps.worldEditWandGuard(),
            breakHandler,
            breakConfig,
            breakSoundConfig,
            deps.breakAnimationSender(),
            materials.wire(),
            materials.storageCarrier(),
            materials.terminalCarrier(),
            materials.monitorCarrier(),
            materials.busCarrier(),
            materials.relayCarrier(),
            materials.chunkLoaderCarrier());
    registerCustomBreakingPackets(deps, customBlockBreaker);
    return new RuntimeBreakingServices(breakHandler, breakSoundConfig, customBlockBreaker);
  }

  private static void registerCustomBreakingPackets(
      RuntimeBreakingServicesDependencies deps, CustomBlockBreaker customBlockBreaker) {
    PacketEnhancements packetEnhancements = deps.packetEnhancements();
    if (!shouldRegisterCustomBreakingPackets(packetEnhancements)) {
      return;
    }
    PacketEnhancements.CustomBreakingPackets packets =
        packetEnhancements.tryCreateCustomBreakingPackets(customBlockBreaker);
    if (packets == null) {
      ExortLog.warn("[Breaking] Vanilla-like packet controls unavailable; using Paper fallback.");
    }
  }

  static boolean shouldRegisterCustomBreakingPackets(PacketEnhancements packetEnhancements) {
    return packetEnhancements != null;
  }
}
