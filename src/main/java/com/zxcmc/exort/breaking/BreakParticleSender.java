package com.zxcmc.exort.breaking;

import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class BreakParticleSender implements BreakAnimationSender {
  private static final double PARTICLE_SPEED = 0.004;
  private static final Predicate<BreakType> ALL_STAGE_TYPES = ignored -> true;

  private final Plugin plugin;
  private final double range;
  private final double rangeSquared;
  private final int stageCount;
  private final int breakCount;
  private final double spread;
  private final BlockDataResolver blockDataResolver;
  private final boolean typeAwareParticleBlockData;
  private final Predicate<BreakType> stageParticleFilter;

  private BreakParticleSender(
      Plugin plugin,
      Settings settings,
      BlockDataResolver blockDataResolver,
      boolean typeAwareParticleBlockData,
      Predicate<BreakType> stageParticleFilter) {
    this.plugin = plugin;
    this.range = Math.max(0.0, settings.range());
    this.rangeSquared = this.range * this.range;
    this.stageCount = Math.max(0, settings.stageCount());
    this.breakCount = Math.max(0, settings.breakCount());
    this.spread = Math.max(0.0, settings.spread());
    this.blockDataResolver = blockDataResolver == null ? this::visualBlockData : blockDataResolver;
    this.typeAwareParticleBlockData = typeAwareParticleBlockData;
    this.stageParticleFilter = stageParticleFilter == null ? ALL_STAGE_TYPES : stageParticleFilter;
  }

  public static BreakParticleSender vanilla(Plugin plugin, Settings settings) {
    return new BreakParticleSender(plugin, settings, null, true, ALL_STAGE_TYPES);
  }

  public static BreakParticleSender resource(
      Plugin plugin, Settings settings, Material particleMaterial) {
    BlockData blockData =
        particleMaterial != null && particleMaterial.isBlock()
            ? particleMaterial.createBlockData()
            : null;
    return new BreakParticleSender(
        plugin, settings, (block, type) -> blockData, false, ALL_STAGE_TYPES);
  }

  public static BreakParticleSender resource(
      Plugin plugin, Settings settings, BlockDataResolver blockDataResolver) {
    return resource(plugin, settings, blockDataResolver, ALL_STAGE_TYPES);
  }

  public static BreakParticleSender resource(
      Plugin plugin,
      Settings settings,
      BlockDataResolver blockDataResolver,
      Predicate<BreakType> stageParticleFilter) {
    BlockDataResolver resolver =
        blockDataResolver == null ? (block, type) -> null : blockDataResolver;
    return new BreakParticleSender(plugin, settings, resolver, false, stageParticleFilter);
  }

  boolean usesTypeAwareParticleBlockData() {
    return typeAwareParticleBlockData;
  }

  boolean showsStageParticlesFor(BreakType type) {
    return stageParticleFilter.test(type);
  }

  @Override
  public void show(Block block, BreakType type, double progress) {
    if (!showsStageParticlesFor(type)) {
      return;
    }
    spawn(block, type, stageCount);
  }

  @Override
  public void breakBlock(Block block, BreakType type) {
    spawn(block, type, breakCount);
  }

  @Override
  public void clear(Block block) {
    // Particles are client-side one-shot visuals.
  }

  private void spawn(Block block, BreakType type, int particleCount) {
    if (block == null || block.getWorld() == null || range <= 0.0 || particleCount <= 0) {
      return;
    }
    Location loc = block.getLocation().add(0.5, 0.5, 0.5);
    for (Player viewer : block.getWorld().getPlayers()) {
      if (viewer.getLocation().distanceSquared(loc) <= rangeSquared) {
        spawnFor(viewer, loc, block, type, particleCount);
      }
    }
  }

  private void spawnFor(
      Player viewer, Location loc, Block block, BreakType type, int particleCount) {
    BlockData blockData = blockDataResolver.resolve(block, type);
    if (blockData == null) {
      return;
    }
    viewer.spawnParticle(
        Particle.BLOCK,
        loc,
        particleCount,
        spread,
        spread,
        spread,
        PARTICLE_SPEED,
        blockData,
        false);
  }

  private BlockData visualBlockData(Block block, BreakType type) {
    String material =
        switch (type) {
          case TERMINAL -> terminalMaterial(block);
          case MONITOR -> "SMOOTH_STONE";
          case BUS -> busMaterial(block);
          case RELAY -> "LODESTONE";
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

  public record Settings(double range, int stageCount, int breakCount, double spread) {}

  @FunctionalInterface
  public interface BlockDataResolver {
    BlockData resolve(Block block, BreakType type);
  }
}
