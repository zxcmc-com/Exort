package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BreakAnimationSender;
import com.zxcmc.exort.breaking.BreakParticleSender;
import com.zxcmc.exort.breaking.BreakType;
import com.zxcmc.exort.breaking.BreakVisualConfig;
import com.zxcmc.exort.breaking.CompositeBreakAnimationSender;
import com.zxcmc.exort.breaking.overlay.BreakingOverlayModelResolver;
import com.zxcmc.exort.breaking.overlay.DisplayBreakAnimationSender;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.carrier.ChorusPlantVisualState;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

public final class RuntimeBreakAnimationSenders {
  private RuntimeBreakAnimationSenders() {}

  public static BreakAnimationSender create(
      Plugin plugin, boolean resourceMode, String resourceNamespace, RuntimeMaterials materials) {
    BreakVisualConfig visualConfig = BreakVisualConfig.defaults();
    DisplayBreakAnimationSender.clearStaleOverlays();
    if (resourceMode) {
      List<BreakAnimationSender> senders = new ArrayList<>();
      BreakVisualConfig.ResourceOverlayConfig overlay = visualConfig.resourceOverlay();
      if (overlay.enabled()) {
        Material base =
            RuntimeMaterialResolver.resolve(overlay.displayBaseMaterial(), Material.PAPER);
        BreakingOverlayModelResolver resolver =
            new BreakingOverlayModelResolver(
                plugin,
                materials.wire(),
                materials.terminalCarrier(),
                materials.storageCarrier(),
                materials.monitorCarrier(),
                materials.busCarrier(),
                materials.relayCarrier());
        senders.add(
            new DisplayBreakAnimationSender(
                plugin,
                base,
                resourceNamespace,
                overlay.modelRoot(),
                overlay.displayScale(),
                resolver));
      }
      if (visualConfig.resourceParticles().enabled()) {
        senders.add(
            createResourceParticleSender(
                plugin, visualConfig.resourceParticles(), materials.wire()));
      }
      return CompositeBreakAnimationSender.of(senders);
    }

    if (!visualConfig.vanillaParticles().enabled()) {
      return BreakAnimationSender.NOOP;
    }
    return BreakParticleSender.vanilla(plugin, visualConfig.vanillaParticles().settings());
  }

  private static BreakAnimationSender createResourceParticleSender(
      Plugin plugin,
      BreakVisualConfig.ResourceParticleConfig particleConfig,
      Material wireMaterial) {
    BlockData terminalMonitorBus =
        resourceBlockData(
            plugin, ChorusPlantVisualState.TERMINAL_MONITOR_BUS_PROXY, Material.NETHERITE_BLOCK);
    BlockData storage =
        resourceBlockData(plugin, ChorusPlantVisualState.STORAGE_PROXY, Material.NETHERITE_BLOCK);
    BlockData wire =
        resourceBlockData(plugin, ChorusPlantVisualState.NONE, Material.BLACK_STAINED_GLASS);
    BlockData fallback = Material.NETHERITE_BLOCK.createBlockData();

    return BreakParticleSender.resource(
        plugin,
        particleConfig.settings(),
        (block, type) ->
            switch (type) {
              case WIRE -> wire;
              case STORAGE -> storage;
              case TERMINAL, MONITOR, BUS, RELAY -> terminalMonitorBus;
              case NONE -> fallback;
            },
        type -> shouldShowResourceStageParticles(wireMaterial, type));
  }

  static boolean shouldShowResourceStageParticles(Material wireMaterial, BreakType type) {
    return type != BreakType.WIRE || wireMaterial != Carriers.CHORUS_MATERIAL;
  }

  private static BlockData resourceBlockData(
      Plugin plugin, ChorusPlantVisualState state, Material fallback) {
    try {
      return state.createBlockData();
    } catch (RuntimeException e) {
      if (plugin != null) {
        plugin
            .getLogger()
            .warning(
                "Failed to create RESOURCE break particle block data for "
                    + state.stateKey()
                    + "; falling back to "
                    + fallback);
      }
      return fallback.createBlockData();
    }
  }
}
