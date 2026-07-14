package com.zxcmc.exort.placement.bridge;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;

final class BridgePlacementPolicy {
  private BridgePlacementPolicy() {}

  static boolean isReplaceable(Block block) {
    if (block == null) return false;
    Material type = block.getType();
    return type == Material.AIR
        || type == Material.CAVE_AIR
        || type == Material.VOID_AIR
        || block.isReplaceable();
  }

  static boolean hasPlacementSpace(Block target) {
    Objects.requireNonNull(target, "target");
    BoundingBox box =
        new BoundingBox(
            target.getX(),
            target.getY(),
            target.getZ(),
            target.getX() + 1,
            target.getY() + 1,
            target.getZ() + 1);
    return target.getWorld().getNearbyEntities(box).stream()
        .noneMatch(LivingEntity.class::isInstance);
  }

  static boolean shouldUseOffhand(
      EquipmentSlot hand,
      boolean mainHandEmpty,
      boolean mainHandCustom,
      boolean mainHandBlock,
      boolean mainHandEdible,
      int foodLevel) {
    Objects.requireNonNull(hand, "hand");
    if (hand != EquipmentSlot.OFF_HAND) return true;
    if (mainHandEmpty) return true;
    if (mainHandCustom) return false;
    if (mainHandEdible && foodLevel >= 20) return true;
    return !mainHandBlock && !mainHandEdible;
  }
}
