package com.zxcmc.exort.recipes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DefaultRecipesValidationTest {
  private static final Set<String> FIXED_EXORT_ITEMS =
      Set.of(
          "storage_core",
          "terminal",
          "crafting_terminal",
          "monitor",
          "import_bus",
          "export_bus",
          "wire",
          "wireless_terminal");

  @Test
  void bundledRecipesReferenceValidItemsAndMaterials() {
    YamlConfiguration recipes =
        YamlConfiguration.loadConfiguration(new File("src/main/resources/recipes.yml"));
    Set<String> tiers = storageTiers();

    validateShaped(recipes.getConfigurationSection("shaped"), tiers);
    validateShapeless(recipes.getConfigurationSection("shapeless"), tiers);
    validateSmithing(recipes.getConfigurationSection("smithing"), tiers);
  }

  private static void validateShaped(ConfigurationSection shaped, Set<String> tiers) {
    assertNotNull(shaped, "missing shaped section");
    for (String id : shaped.getKeys(false)) {
      ConfigurationSection recipe = shaped.getConfigurationSection(id);
      assertNotNull(recipe, id);
      validateResult(recipe.getConfigurationSection("result"), tiers, id);
      List<String> shape = recipe.getStringList("shape");
      assertFalse(shape.isEmpty(), id + " shape is empty");
      assertTrue(shape.size() <= 3, id + " shape is taller than 3");
      int width = shape.getFirst().length();
      assertTrue(width > 0 && width <= 3, id + " shape width must be 1..3");
      Set<Character> used = new HashSet<>();
      for (String row : shape) {
        assertTrue(row.length() == width, id + " shape rows must have equal width");
        for (char symbol : row.toCharArray()) {
          if (symbol != '_') {
            used.add(symbol);
          }
        }
      }
      ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
      assertNotNull(ingredients, id + " ingredients missing");
      for (char symbol : used) {
        String key = String.valueOf(symbol);
        assertTrue(ingredients.contains(key), id + " missing ingredient " + key);
        validateIngredientId(ingredients.getString(key), tiers, id + ":" + key);
      }
    }
  }

  private static void validateShapeless(ConfigurationSection shapeless, Set<String> tiers) {
    assertNotNull(shapeless, "missing shapeless section");
    for (String id : shapeless.getKeys(false)) {
      ConfigurationSection recipe = shapeless.getConfigurationSection(id);
      assertNotNull(recipe, id);
      validateResult(recipe.getConfigurationSection("result"), tiers, id);
      List<String> ingredients = recipe.getStringList("ingredients");
      assertTrue(
          !ingredients.isEmpty() && ingredients.size() <= 9, id + " ingredients must be 1..9");
      for (String ingredient : ingredients) {
        validateIngredientId(ingredient, tiers, id);
      }
    }
  }

  private static void validateSmithing(ConfigurationSection smithing, Set<String> tiers) {
    assertNotNull(smithing, "missing smithing section");
    for (String id : smithing.getKeys(false)) {
      ConfigurationSection recipe = smithing.getConfigurationSection(id);
      assertNotNull(recipe, id);
      validateResult(recipe.getConfigurationSection("result"), tiers, id);
      validateIngredientId(recipe.getString("template"), tiers, id + ":template");
      validateIngredientId(recipe.getString("base"), tiers, id + ":base");
      validateIngredientId(recipe.getString("addition"), tiers, id + ":addition");
    }
  }

  private static void validateResult(ConfigurationSection result, Set<String> tiers, String id) {
    assertNotNull(result, id + " result missing");
    validateIngredientId(result.getString("item"), tiers, id + ":result");
    assertTrue(result.getInt("amount", 1) > 0, id + " result amount must be positive");
  }

  private static void validateIngredientId(String raw, Set<String> tiers, String context) {
    assertNotNull(raw, context + " id missing");
    String id = raw.trim().toLowerCase();
    assertFalse(id.isEmpty(), context + " id blank");
    if (id.startsWith("exort:")) {
      String local = id.substring("exort:".length());
      if (local.startsWith("storage:")) {
        assertTrue(tiers.contains(local.substring("storage:".length())), context + " unknown tier");
      } else {
        assertTrue(FIXED_EXORT_ITEMS.contains(local), context + " unknown Exort item");
      }
      return;
    }
    if (id.startsWith("#")) {
      assertNotNull(RecipeService.parseNamespacedKey(id.substring(1)), context + " tag malformed");
      return;
    }
    String materialId = id;
    if (id.startsWith("minecraft:")) {
      materialId = id.substring("minecraft:".length());
    }
    assertNotNull(Material.matchMaterial(materialId), context + " unknown material " + raw);
  }

  private static Set<String> storageTiers() {
    YamlConfiguration config =
        YamlConfiguration.loadConfiguration(new File("src/main/resources/storage-tiers.yml"));
    ConfigurationSection tiers = config.getConfigurationSection("tiers");
    assertNotNull(tiers, "storage tiers missing");
    return tiers.getKeys(false).stream()
        .map(String::toLowerCase)
        .collect(java.util.stream.Collectors.toSet());
  }
}
