package com.zxcmc.exort.recipes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftingRulesConfigTest {
  @Test
  void defaultsMatchCurrentCraftingRules() {
    CraftingRulesConfig config = CraftingRulesConfig.defaults();

    assertTrue(config.blockVanilla());
    assertTrue(config.allowExternal());
  }
}
