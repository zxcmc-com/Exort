package com.zxcmc.exort.recipes;

public record CraftingRulesConfig(boolean blockVanilla, boolean allowExternal) {
  public static CraftingRulesConfig defaults() {
    return new CraftingRulesConfig(true, true);
  }
}
