package com.zxcmc.exort.recipes;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record CraftingRulesConfig(boolean blockVanilla, boolean allowExternal) {
  public static CraftingRulesConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new CraftingRulesConfig(
        config.getBoolean("crafting.blockVanilla", true),
        config.getBoolean("crafting.allowExternal", true));
  }
}
