package com.zxcmc.exort.core.breaking;

import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.TerminalKind;
import com.zxcmc.exort.core.marker.TerminalMarker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

public final class BreakParticleSender implements BreakAnimationSender {
  private final ExortPlugin plugin;
  private final double range;
  private final double rangeSquared;
  private final int count;
  private final double spread;
  private final double speed;
  private final BlockData particleBlockData;

  private BreakParticleSender(ExortPlugin plugin, Settings settings, Material particleMaterial) {
    this.plugin = plugin;
    this.range = Math.max(0.0, settings.range());
    this.rangeSquared = this.range * this.range;
    this.count = Math.max(0, settings.count());
    this.spread = Math.max(0.0, settings.spread());
    this.speed = Math.max(0.0, settings.speed());
    this.particleBlockData =
        particleMaterial != null && particleMaterial.isBlock()
            ? particleMaterial.createBlockData()
            : null;
  }

  public static BreakParticleSender vanilla(ExortPlugin plugin, Settings settings) {
    return new BreakParticleSender(plugin, settings, null);
  }

  public static BreakParticleSender resource(
      ExortPlugin plugin, Settings settings, Material particleMaterial) {
    return new BreakParticleSender(plugin, settings, particleMaterial);
  }

  @Override
  public void show(Block block, BreakType type, double progress) {
    if (block == null || block.getWorld() == null || range <= 0.0 || count <= 0) {
      return;
    }
    Location loc = block.getLocation().add(0.5, 0.5, 0.5);
    for (Player viewer : block.getWorld().getPlayers()) {
      if (viewer.getLocation().distanceSquared(loc) <= rangeSquared) {
        spawnFor(viewer, loc, block, type);
      }
    }
  }

  @Override
  public void breakBlock(Block block, BreakType type) {
    show(block, type, 1.0);
  }

  @Override
  public void clear(Block block) {
    // Particles are client-side one-shot visuals.
  }

  private void spawnFor(Player viewer, Location loc, Block block, BreakType type) {
    BlockData blockData =
        particleBlockData != null ? particleBlockData : visualBlockData(block, type);
    viewer.spawnParticle(
        Particle.BLOCK, loc, count, spread, spread, spread, speed, blockData, false);
  }

  private BlockData visualBlockData(Block block, BreakType type) {
    String material =
        switch (type) {
          case TERMINAL -> terminalMaterial(block);
          case MONITOR -> "SMOOTH_STONE";
          case BUS -> busMaterial(block);
          case WIRE -> "BLACK_STAINED_GLASS";
          case STORAGE -> "VAULT";
          default -> "STONE";
        };
    Material matched = Material.matchMaterial(material);
    if (matched == null || !matched.isBlock()) {
      matched = Material.STONE;
    }
    return matched.createBlockData();
  }

  private String terminalMaterial(Block block) {
    return TerminalMarker.kind(plugin, block) == TerminalKind.CRAFTING
        ? "CRAFTING_TABLE"
        : "BARREL";
  }

  private String busMaterial(Block block) {
    return BusMarker.get(plugin, block)
        .map(data -> data.type() == BusType.EXPORT ? "DROPPER" : "DISPENSER")
        .orElse("DISPENSER");
  }

  public record Settings(double range, int count, double spread, double speed) {}
}
