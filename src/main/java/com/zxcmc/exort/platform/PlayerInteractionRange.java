package com.zxcmc.exort.platform;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public final class PlayerInteractionRange {
  private static final double FALLBACK_BLOCK_INTERACTION_RANGE = 8.0D;

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
    return Double.isFinite(value) && value > 0.0D ? value : FALLBACK_BLOCK_INTERACTION_RANGE;
  }
}
