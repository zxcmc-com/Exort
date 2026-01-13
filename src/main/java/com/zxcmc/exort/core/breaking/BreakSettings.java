package com.zxcmc.exort.core.breaking;

import java.util.Collections;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class BreakSettings {
  private final double hardness;
  private final Set<Material> effectiveTools;

  public BreakSettings(double hardness, Set<Material> effectiveTools) {
    this.hardness = hardness;
    this.effectiveTools =
        effectiveTools == null ? Collections.emptySet() : Set.copyOf(effectiveTools);
  }

  public double hardness() {
    return hardness;
  }

  public boolean isEffective(ItemStack tool) {
    if (tool == null) return false;
    return effectiveTools.contains(tool.getType());
  }
}
