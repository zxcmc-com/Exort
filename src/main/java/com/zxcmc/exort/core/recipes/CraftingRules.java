package com.zxcmc.exort.core.recipes;

import com.zxcmc.exort.core.keys.StorageKeys;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmithingTrimRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CraftingRules {
  private final StorageKeys keys;
  private final boolean blockVanilla;
  private final boolean allowExternal;

  public CraftingRules(StorageKeys keys, boolean blockVanilla, boolean allowExternal) {
    this.keys = keys;
    this.blockVanilla = blockVanilla;
    this.allowExternal = allowExternal;
  }

  public boolean shouldBlock(ItemStack[] matrix, Recipe recipe) {
    if (!containsCustomItem(matrix)) return false;
    if (!blockVanilla) return false;
    boolean allowsCustom = recipeAllowsCustomItem(recipe);
    if (allowsCustom) {
      if (!allowExternal && isExternalRecipe(recipe)) {
        return true;
      }
      return false;
    }
    return true;
  }

  private boolean containsCustomItem(ItemStack[] matrix) {
    if (matrix == null) return false;
    for (ItemStack stack : matrix) {
      if (isCustomItem(stack)) return true;
    }
    return false;
  }

  public boolean isCustomItem(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return type != null && !type.isEmpty();
  }

  private boolean isExternalRecipe(Recipe recipe) {
    if (!(recipe instanceof Keyed keyed)) return true;
    NamespacedKey key = keyed.getKey();
    if (key == null) return true;
    String ns = key.getNamespace();
    return !"minecraft".equalsIgnoreCase(ns) && !"exort".equalsIgnoreCase(ns);
  }

  private boolean recipeAllowsCustomItem(Recipe recipe) {
    if (recipe == null) return false;
    if (recipe instanceof ShapedRecipe shaped) {
      for (RecipeChoice choice : shaped.getChoiceMap().values()) {
        if (choiceAllowsCustomItem(choice)) return true;
      }
      return false;
    }
    if (recipe instanceof ShapelessRecipe shapeless) {
      for (RecipeChoice choice : shapeless.getChoiceList()) {
        if (choiceAllowsCustomItem(choice)) return true;
      }
      return false;
    }
    if (recipe instanceof StonecuttingRecipe stonecutting) {
      return choiceAllowsCustomItem(stonecutting.getInputChoice());
    }
    if (recipe instanceof CookingRecipe<?> cooking) {
      return choiceAllowsCustomItem(cooking.getInputChoice());
    }
    if (recipe instanceof SmithingTransformRecipe smithing) {
      return choiceAllowsCustomItem(smithing.getBase())
          || choiceAllowsCustomItem(smithing.getAddition())
          || choiceAllowsCustomItem(smithing.getTemplate());
    }
    if (recipe instanceof SmithingTrimRecipe smithing) {
      return choiceAllowsCustomItem(smithing.getBase())
          || choiceAllowsCustomItem(smithing.getAddition())
          || choiceAllowsCustomItem(smithing.getTemplate());
    }
    return false;
  }

  private boolean choiceAllowsCustomItem(RecipeChoice choice) {
    if (choice == null) return false;
    if (choice instanceof RecipeChoice.ExactChoice exact) {
      for (ItemStack item : exact.getChoices()) {
        if (isCustomItem(item)) return true;
      }
    }
    return false;
  }
}
