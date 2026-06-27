package com.zxcmc.exort.recipes;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RecipeRuntimeConfigTest {
  @Test
  void readsConfiguredValue() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("recipes.enabled", false);

    RecipeRuntimeConfig config = RecipeRuntimeConfig.fromConfig(yaml);

    assertFalse(config.enabled());
  }
}
