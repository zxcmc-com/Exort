package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BreakAnimationSender;
import com.zxcmc.exort.breaking.BreakParticleSender;
import com.zxcmc.exort.breaking.BreakVisualConfig;
import com.zxcmc.exort.breaking.CompositeBreakAnimationSender;
import com.zxcmc.exort.display.DisplayBreakAnimationSender;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class RuntimeBreakAnimationSenders {
  private RuntimeBreakAnimationSenders() {}

  public static BreakAnimationSender create(
      Plugin plugin, FileConfiguration config, boolean resourceMode, String resourceNamespace) {
    BreakVisualConfig visualConfig = BreakVisualConfig.fromConfig(config);
    DisplayBreakAnimationSender.clearStaleOverlays();
    if (resourceMode) {
      List<BreakAnimationSender> senders = new ArrayList<>();
      BreakVisualConfig.ResourceOverlayConfig overlay = visualConfig.resourceOverlay();
      if (overlay.enabled()) {
        Material base =
            RuntimeMaterialResolver.resolve(overlay.displayBaseMaterial(), Material.PAPER);
        senders.add(
            new DisplayBreakAnimationSender(
                plugin, base, resourceNamespace, overlay.modelPrefix(), overlay.displayScale()));
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
    Material material =
        RuntimeMaterialResolver.resolve(particleConfig.materialName(), Material.NETHERITE_BLOCK);
    if (material == null || !material.isBlock()) {
      plugin
          .getLogger()
          .warning(
              "Invalid RESOURCE break particle block material '"
                  + particleConfig.materialName()
                  + "', falling back to NETHERITE_BLOCK");
      material = Material.NETHERITE_BLOCK;
    }
    return BreakParticleSender.resource(plugin, particleConfig.settings(), material);
  }
}
