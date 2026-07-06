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
          "relay",
          "wire",
          "chunk_loader",
          "personal_chunk_loader",
          "dormant_chunk_loader",
          "wireless_terminal");

  @Test
  void bundledRecipesReferenceValidItemsAndMaterials() {
    YamlConfiguration recipes =
        YamlConfiguration.loadConfiguration(new File("src/main/resources/recipes.yml"));
    Set<String> tiers = storageTiers();

    validateShaped(recipes.getConfigurationSection("shaped"), tiers);
    validateShapeless(recipes.getConfigurationSection("shapeless"), tiers);
    validateSmithing(recipes.getConfigurationSection("smithing"), tiers);
    validateCooking(recipes.getConfigurationSection("furnace"), tiers);
    validateCooking(recipes.getConfigurationSection("blasting"), tiers);
    validateCooking(recipes.getConfigurationSection("smoking"), tiers);
    validateCooking(recipes.getConfigurationSection("campfire"), tiers);
    validateStonecutting(recipes.getConfigurationSection("stonecutting"), tiers);
  }

  @Test
  void bundledRecipesDoNotCraftStorageTierItemsByDefault() {
    YamlConfiguration recipes =
        YamlConfiguration.loadConfiguration(new File("src/main/resources/recipes.yml"));

    assertNoStorageResults(recipes.getConfigurationSection("shaped"));
    assertNoStorageResults(recipes.getConfigurationSection("shapeless"));
    assertNoStorageResults(recipes.getConfigurationSection("smithing"));
    assertNoStorageResultsIfPresent(recipes.getConfigurationSection("furnace"));
    assertNoStorageResultsIfPresent(recipes.getConfigurationSection("blasting"));
    assertNoStorageResultsIfPresent(recipes.getConfigurationSection("smoking"));
    assertNoStorageResultsIfPresent(recipes.getConfigurationSection("campfire"));
    assertNoStorageResultsIfPresent(recipes.getConfigurationSection("stonecutting"));
  }

  private static void assertNoStorageResults(ConfigurationSection section) {
    assertNotNull(section, "missing recipe section");
    for (String id : section.getKeys(false)) {
      ConfigurationSection result = section.getConfigurationSection(id + ".result");
      assertNotNull(result, id + " result missing");
      String item = result.getString("item", "").trim().toLowerCase();
      assertFalse(item.startsWith("exort:storage:"), id + " crafts a storage tier by default");
    }
  }

  private static void assertNoStorageResultsIfPresent(ConfigurationSection section) {
    if (section != null) {
      assertNoStorageResults(section);
    }
  }

  private static void validateShaped(ConfigurationSection shaped, Set<String> tiers) {
    assertNotNull(shaped, "missing shaped section");
    for (String id : shaped.getKeys(false)) {
      ConfigurationSection recipe = shaped.getConfigurationSection(id);
      assertNotNull(recipe, id);
      validateResult(recipe.getConfigurationSection("result"), tiers, id);
      validateCraftingMetadata(recipe, id);
      validateUnlock(recipe, tiers, id);
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
      validateCraftingMetadata(recipe, id);
      validateUnlock(recipe, tiers, id);
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
      validateUnlock(recipe, tiers, id);
      validateIngredientId(recipe.getString("template"), tiers, id + ":template");
      validateIngredientId(recipe.getString("base"), tiers, id + ":base");
      validateIngredientId(recipe.getString("addition"), tiers, id + ":addition");
      if (recipe.contains("copyDataComponents")) {
        assertTrue(
            recipe.get("copyDataComponents") instanceof Boolean,
            id + " copyDataComponents must be boolean");
      }
    }
  }

  private static void validateCooking(ConfigurationSection cooking, Set<String> tiers) {
    if (cooking == null) {
      return;
    }
    for (String id : cooking.getKeys(false)) {
      ConfigurationSection recipe = cooking.getConfigurationSection(id);
      assertNotNull(recipe, id);
      validateResult(recipe.getConfigurationSection("result"), tiers, id);
      validateUnlock(recipe, tiers, id);
      validateIngredientId(recipe.getString("input"), tiers, id + ":input");
      Object rawExperience = recipe.get("experience");
      assertTrue(
          rawExperience == null || rawExperience instanceof Number,
          id + " experience must be numeric");
      double experience = rawExperience instanceof Number number ? number.doubleValue() : 0.0D;
      assertTrue(
          Double.isFinite(experience) && experience >= 0.0D,
          id + " experience must be non-negative finite number");
      if (recipe.contains("cookingTime")) {
        Object rawCookingTime = recipe.get("cookingTime");
        assertTrue(rawCookingTime instanceof Number, id + " cookingTime must be numeric");
        double cookingTime = ((Number) rawCookingTime).doubleValue();
        assertTrue(
            Double.isFinite(cookingTime)
                && cookingTime > 0.0D
                && cookingTime <= Integer.MAX_VALUE
                && cookingTime % 1.0D == 0.0D,
            id + " cookingTime must be a positive integer");
      }
      validateCookingMetadata(recipe, id);
    }
  }

  private static void validateStonecutting(ConfigurationSection stonecutting, Set<String> tiers) {
    if (stonecutting == null) {
      return;
    }
    for (String id : stonecutting.getKeys(false)) {
      ConfigurationSection recipe = stonecutting.getConfigurationSection(id);
      assertNotNull(recipe, id);
      validateResult(recipe.getConfigurationSection("result"), tiers, id);
      validateUnlock(recipe, tiers, id);
      validateIngredientId(recipe.getString("input"), tiers, id + ":input");
      validateGroup(recipe, id);
    }
  }

  private static void validateResult(ConfigurationSection result, Set<String> tiers, String id) {
    assertNotNull(result, id + " result missing");
    String item = result.getString("item");
    assertNotNull(item, id + " result item missing");
    assertTrue(
        item.trim().toLowerCase().startsWith("exort:"), id + " result must be an Exort item");
    validateIngredientId(item, tiers, id + ":result");
    assertTrue(result.getInt("amount", 1) > 0, id + " result amount must be positive");
  }

  private static void validateCraftingMetadata(ConfigurationSection recipe, String id) {
    validateGroup(recipe, id);
    assertNotNull(
        RecipeService.parseCraftingCategory(recipe.getString("category")),
        id + " unknown crafting category");
  }

  private static void validateCookingMetadata(ConfigurationSection recipe, String id) {
    validateGroup(recipe, id);
    assertNotNull(
        RecipeService.parseCookingCategory(recipe.getString("category")),
        id + " unknown cooking category");
  }

  private static void validateGroup(ConfigurationSection recipe, String id) {
    if (recipe.contains("group")) {
      Object group = recipe.get("group");
      assertTrue(group instanceof String, id + " group must be string");
      assertFalse(((String) group).isBlank(), id + " group must not be blank");
    }
  }

  private static void validateUnlock(ConfigurationSection recipe, Set<String> tiers, String id) {
    if (!recipe.contains("unlock")) {
      return;
    }
    Object rawUnlock = recipe.get("unlock");
    assertTrue(rawUnlock instanceof List<?>, id + " unlock must be a list");
    List<?> unlocks = (List<?>) rawUnlock;
    assertFalse(unlocks.isEmpty(), id + " unlock must not be empty");
    for (Object raw : unlocks) {
      assertTrue(raw instanceof String, id + " unlock entries must be strings");
      validateIngredientId((String) raw, tiers, id + ":unlock");
    }
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
