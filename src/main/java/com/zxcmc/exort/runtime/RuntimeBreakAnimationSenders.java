package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BreakAnimationSender;
import com.zxcmc.exort.breaking.BreakParticleSender;
import com.zxcmc.exort.breaking.BreakVisualConfig;
import com.zxcmc.exort.breaking.CompositeBreakAnimationSender;
import com.zxcmc.exort.display.BreakingOverlayModelResolver;
import com.zxcmc.exort.display.DisplayBreakAnimationSender;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
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
                materials.busCarrier());
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
        senders.add(createResourceParticleSender(plugin, visualConfig.resourceParticles()));
      }
      return CompositeBreakAnimationSender.of(senders);
    }

    if (!visualConfig.vanillaParticles().enabled()) {
      return BreakAnimationSender.NOOP;
    }
    return BreakParticleSender.vanilla(plugin, visualConfig.vanillaParticles().settings());
  }

  private static BreakAnimationSender createResourceParticleSender(
      Plugin plugin, BreakVisualConfig.ResourceParticleConfig particleConfig) {
    return BreakParticleSender.resource(plugin, particleConfig.settings());
  }
}
