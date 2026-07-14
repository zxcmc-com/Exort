package com.zxcmc.exort.recipes;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;

interface RecipeRegistry {
  boolean add(Recipe recipe);

  boolean remove(NamespacedKey key);

  Recipe get(NamespacedKey key);

  static RecipeRegistry bukkit() {
    return new RecipeRegistry() {
      @Override
      public boolean add(Recipe recipe) {
        return Bukkit.addRecipe(recipe);
      }

      @Override
      public boolean remove(NamespacedKey key) {
        return Bukkit.removeRecipe(key);
      }

      @Override
      public Recipe get(NamespacedKey key) {
        return Bukkit.getRecipe(key);
      }
    };
  }
}
