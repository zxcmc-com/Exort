package com.zxcmc.exort.core.breaking;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class BreakProgressCalculator {
  private BreakProgressCalculator() {}

  public static double computeDamage(Player player, BreakSettings settings) {
    double hardness = settings.hardness();
    if (hardness <= 0.0) return 1.0;
    ItemStack tool = player.getInventory().getItemInMainHand();
    boolean effective = settings.isEffective(tool);
    double speed = effective ? baseSpeed(tool) : 1.0;
    int efficiency =
        (effective && tool != null) ? tool.getEnchantmentLevel(Enchantment.EFFICIENCY) : 0;
    if (efficiency > 0 && speed > 1.0) {
      speed += (efficiency * efficiency + 1);
    }
    PotionEffect haste = player.getPotionEffect(PotionEffectType.HASTE);
    if (haste != null) {
      speed *= 1.0 + (haste.getAmplifier() + 1) * 0.2;
    }
    PotionEffect fatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
    if (fatigue != null) {
      speed *= Math.pow(0.3, fatigue.getAmplifier() + 1);
    }
    if (isInWater(player) && !hasAquaAffinity(player)) {
      speed /= 5.0;
    }
    if (!isOnGround(player)) {
      speed /= 5.0;
    }
    return speed / hardness / 30.0;
  }

  private static double baseSpeed(ItemStack tool) {
    if (tool == null) return 1.0;
    Material type = tool.getType();
    return switch (type) {
      case WOODEN_PICKAXE, WOODEN_AXE -> 2.0;
      case STONE_PICKAXE, STONE_AXE -> 4.0;
      case IRON_PICKAXE, IRON_AXE -> 6.0;
      case DIAMOND_PICKAXE, DIAMOND_AXE -> 8.0;
      case NETHERITE_PICKAXE, NETHERITE_AXE -> 9.0;
      case GOLDEN_PICKAXE, GOLDEN_AXE -> 12.0;
      case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, DIAMOND_SWORD, NETHERITE_SWORD, GOLDEN_SWORD ->
          1.6;
      default -> 1.0;
    };
  }

  private static boolean isInWater(Player player) {
    try {
      return player.isInWater();
    } catch (NoSuchMethodError ignored) {
      return player.getLocation().getBlock().isLiquid();
    }
  }

  private static boolean isOnGround(Player player) {
    var world = player.getWorld();
    var box = player.getBoundingBox();
    double y = box.getMinY() - 0.01;
    int minX = (int) Math.floor(box.getMinX() + 1.0E-6);
    int maxX = (int) Math.floor(box.getMaxX() - 1.0E-6);
    int minZ = (int) Math.floor(box.getMinZ() + 1.0E-6);
    int maxZ = (int) Math.floor(box.getMaxZ() - 1.0E-6);
    int by = (int) Math.floor(y);
    for (int x : new int[] {minX, maxX}) {
      for (int z : new int[] {minZ, maxZ}) {
        if (world.getBlockAt(x, by, z).getType().isSolid()) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasAquaAffinity(Player player) {
    ItemStack helmet = player.getInventory().getHelmet();
    if (helmet == null) return false;
    return helmet.getEnchantmentLevel(Enchantment.AQUA_AFFINITY) > 0;
  }
}
