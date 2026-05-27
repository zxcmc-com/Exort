package com.zxcmc.exort.recipes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CraftingRulesConfigTest {
  @Test
  void defaultsMatchCurrentCraftingRuleConfig() {
    CraftingRulesConfig config = CraftingRulesConfig.fromConfig(new YamlConfiguration());

    assertTrue(config.blockVanilla());
    assertTrue(config.allowExternal());
  }

  @Test
  void configuredValuesOverrideDefaults() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("crafting.blockVanilla", false);
    yaml.set("crafting.allowExternal", false);

    CraftingRulesConfig config = CraftingRulesConfig.fromConfig(yaml);

    assertFalse(config.blockVanilla());
    assertFalse(config.allowExternal());
  }
}
