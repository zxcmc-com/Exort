package com.zxcmc.exort.breaking.explosion;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

final class ExplosionOcclusion {
  private static final float RESISTANCE_PADDING = 0.3F;
  private static final float RESISTANCE_MULTIPLIER = 0.3F;
  private final BlastResistanceLookup blastResistance;

  ExplosionOcclusion() {
    this(Material::getBlastResistance);
  }

  ExplosionOcclusion(BlastResistanceLookup blastResistance) {
    this.blastResistance = Objects.requireNonNull(blastResistance, "blastResistance");
  }

  float attenuation(
      World world,
      Location center,
      Block target,
      double distance,
      double step,
      float availableStrength) {
    if (world == null
        || center == null
        || target == null
        || !Double.isFinite(distance)
        || distance <= 0.0D
        || !Double.isFinite(step)
        || step <= 0.0D
        || !Float.isFinite(availableStrength)
        || availableStrength <= 0.0F) {
      return 0.0F;
    }

    double dx = (target.getX() + 0.5D - center.getX()) / distance;
    double dy = (target.getY() + 0.5D - center.getY()) / distance;
    double dz = (target.getZ() + 0.5D - center.getZ()) / distance;
    int originX = floor(center.getX());
    int originY = floor(center.getY());
    int originZ = floor(center.getZ());
    int sampledX = Integer.MIN_VALUE;
    int sampledY = Integer.MIN_VALUE;
    int sampledZ = Integer.MIN_VALUE;
    float sampledAttenuation = 0.0F;
    float attenuation = 0.0F;

    for (double travelled = step; travelled < distance; travelled += step) {
      int x = floor(center.getX() + dx * travelled);
      int y = floor(center.getY() + dy * travelled);
      int z = floor(center.getZ() + dz * travelled);
      if (sameBlock(x, y, z, originX, originY, originZ)
          || sameBlock(x, y, z, target.getX(), target.getY(), target.getZ())) {
        continue;
      }
      if (!sameBlock(x, y, z, sampledX, sampledY, sampledZ)) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
          return Float.POSITIVE_INFINITY;
        }
        Material material = world.getBlockAt(x, y, z).getType();
        sampledX = x;
        sampledY = y;
        sampledZ = z;
        sampledAttenuation =
            isAir(material)
                ? 0.0F
                : (Math.max(0.0F, blastResistance.forMaterial(material)) + RESISTANCE_PADDING)
                    * RESISTANCE_MULTIPLIER;
      }
      attenuation += sampledAttenuation;
      if (!Float.isFinite(attenuation) || attenuation >= availableStrength) {
        return attenuation;
      }
    }
    return attenuation;
  }

  private static int floor(double value) {
    return (int) Math.floor(value);
  }

  private static boolean isAir(Material material) {
    return material == null
        || material == Material.AIR
        || material == Material.CAVE_AIR
        || material == Material.VOID_AIR;
  }

  private static boolean sameBlock(
      int x, int y, int z, int expectedX, int expectedY, int expectedZ) {
    return x == expectedX && y == expectedY && z == expectedZ;
  }

  @FunctionalInterface
  interface BlastResistanceLookup {
    float forMaterial(Material material);
  }
}
