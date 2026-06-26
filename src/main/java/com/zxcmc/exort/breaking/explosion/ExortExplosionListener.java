package com.zxcmc.exort.breaking.explosion;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.runtime.RuntimeMaterials;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Explosive;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExortExplosionListener implements Listener {
  private static final int MAX_PRIME_RADIUS_CACHE_SIZE = 256;
  private static final float BED_OR_RESPAWN_ANCHOR_RADIUS = 5.0F;

  private final ExortExplosionResolver resolver;
  private final Function<Block, BlockBreakHandler.BreakResult> breaker;
  private final Map<UUID, Float> primedExplosionRadii =
      new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Float> eldest) {
          return size() > MAX_PRIME_RADIUS_CACHE_SIZE;
        }
      };

  public ExortExplosionListener(
      JavaPlugin plugin, RuntimeMaterials materials, BlockBreakHandler breakHandler) {
    this(
        new ExortExplosionResolver(
            plugin, materials, ExortBlastResistance.fromConfig(plugin.getConfig())),
        block -> breakHandler.handleBreak(null, block, false));
    Objects.requireNonNull(breakHandler, "breakHandler");
  }

  ExortExplosionListener(
      ExortExplosionResolver resolver, Function<Block, BlockBreakHandler.BreakResult> breaker) {
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.breaker = Objects.requireNonNull(breaker, "breaker");
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onExplosionPrime(ExplosionPrimeEvent event) {
    UUID entityId = event.getEntity().getUniqueId();
    if (entityId == null) return;
    primedExplosionRadii.put(entityId, cleanRadius(event.getRadius()));
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onEntityExplode(EntityExplodeEvent event) {
    handleExplosion(event.getLocation(), entityExplosionRadius(event), event.blockList());
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onBlockExplode(BlockExplodeEvent event) {
    Location center = event.getBlock().getLocation().add(0.5D, 0.5D, 0.5D);
    handleExplosion(center, blockExplosionRadius(event, center), event.blockList());
  }

  void handleExplosion(Location center, float radius, List<Block> blockList) {
    if (center == null || center.getWorld() == null) return;
    ExortExplosionResolver.ExplosionPlan plan = resolver.plan(center, radius, blockList);
    if (blockList != null && !blockList.isEmpty()) {
      blockList.removeIf(plan::suppressesVanilla);
    }
    for (ExortExplosionResolver.ExortExplosionBlock target : plan.destroyed()) {
      breaker.apply(target.block());
    }
  }

  private float entityExplosionRadius(EntityExplodeEvent event) {
    UUID entityId = event.getEntity().getUniqueId();
    Float primedRadius = entityId == null ? null : primedExplosionRadii.remove(entityId);
    if (primedRadius != null && primedRadius > 0.0F) {
      return primedRadius;
    }
    if (event.getEntity() instanceof Explosive explosive) {
      float yield = cleanRadius(explosive.getYield());
      if (yield > 0.0F) {
        return yield;
      }
    }
    return resolver.estimateRadius(event.getLocation(), event.blockList());
  }

  private float blockExplosionRadius(BlockExplodeEvent event, Location center) {
    Material explodedType = event.getExplodedBlockState().getType();
    if (explodedType == Material.RESPAWN_ANCHOR || explodedType.name().endsWith("_BED")) {
      return BED_OR_RESPAWN_ANCHOR_RADIUS;
    }
    return resolver.estimateRadius(center, event.blockList());
  }

  private static float cleanRadius(float radius) {
    return Float.isFinite(radius) && radius > 0.0F ? radius : 0.0F;
  }
}
