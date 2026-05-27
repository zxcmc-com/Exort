package com.zxcmc.exort.recipes;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record RecipeRuntimeConfig(boolean enabled) {
  public static RecipeRuntimeConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new RecipeRuntimeConfig(config.getBoolean("recipes.enabled", true));
  }
}
