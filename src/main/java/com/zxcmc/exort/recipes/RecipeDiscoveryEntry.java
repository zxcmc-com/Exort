package com.zxcmc.exort.recipes;

import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

record RecipeDiscoveryEntry(NamespacedKey key, List<RecipeChoice> unlockChoices) {
  RecipeDiscoveryEntry {
    Objects.requireNonNull(key, "key");
    unlockChoices = List.copyOf(Objects.requireNonNull(unlockChoices, "unlockChoices"));
  }

  boolean matchesAny(ItemStack[] stacks) {
    if (stacks == null || unlockChoices.isEmpty()) {
      return false;
    }
    for (ItemStack stack : stacks) {
      if (matches(stack)) {
        return true;
      }
    }
    return false;
  }

  boolean matches(ItemStack stack) {
    if (stack == null || stack.getType() == Material.AIR) {
      return false;
    }
    for (RecipeChoice choice : unlockChoices) {
      if (choice.test(stack)) {
        return true;
      }
    }
    return false;
  }
}
