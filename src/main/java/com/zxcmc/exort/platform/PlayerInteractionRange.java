package com.zxcmc.exort.platform;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public final class PlayerInteractionRange {
  private static final double FALLBACK_BLOCK_INTERACTION_RANGE = 8.0D;
  private static final double PHYSICAL_DEVICE_CLOSE_RANGE_BUFFER = 4.0D;

  private PlayerInteractionRange() {}

  public static double blockInteractionRangeSquared(Player player) {
    double range = blockInteractionRange(player);
    return range * range;
  }

  public static double blockInteractionRange(Player player) {
    if (player == null) {
      return FALLBACK_BLOCK_INTERACTION_RANGE;
    }
    AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
    double value = attribute == null ? FALLBACK_BLOCK_INTERACTION_RANGE : attribute.getValue();
    return normalizeBlockInteractionRange(value);
  }

  public static double physicalDeviceCloseRangeSquared(Player player) {
    double range = physicalDeviceCloseRange(player);
    return range * range;
  }

  public static double physicalDeviceCloseRange(Player player) {
    return physicalDeviceCloseRange(blockInteractionRange(player));
  }

  static double physicalDeviceCloseRange(double blockInteractionRange) {
    return normalizeBlockInteractionRange(blockInteractionRange)
        + PHYSICAL_DEVICE_CLOSE_RANGE_BUFFER;
  }

  static double normalizeBlockInteractionRange(double value) {
    return Double.isFinite(value) && value > 0.0D ? value : FALLBACK_BLOCK_INTERACTION_RANGE;
  }
}
