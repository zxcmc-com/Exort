package com.zxcmc.exort.recipes;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.infra.config.FeatureAccessConfig;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.java.JavaPlugin;

public final class RecipeService {
  private final JavaPlugin plugin;
  private final CustomItems customItems;
  private final WirelessTerminalService wirelessService;
  private final Supplier<FeatureAccessConfig> featureAccess;
  private final ChoiceFactory choiceFactory;
  private final List<NamespacedKey> registered = new ArrayList<>();
  private final List<RecipeDiscoveryEntry> discoveryEntries = new ArrayList<>();

  public RecipeService(
      JavaPlugin plugin, CustomItems customItems, WirelessTerminalService wirelessService) {
    this(
        plugin,
        customItems,
        wirelessService,
        () ->
            plugin == null
                ? FeatureAccessConfig.defaults()
                : FeatureAccessConfig.fromConfig(plugin.getConfig()));
  }

  RecipeService(
      JavaPlugin plugin,
      CustomItems customItems,
      WirelessTerminalService wirelessService,
      Supplier<FeatureAccessConfig> featureAccess) {
    this(plugin, customItems, wirelessService, featureAccess, ChoiceFactory.bukkit());
  }

  RecipeService(
      JavaPlugin plugin,
      CustomItems customItems,
      WirelessTerminalService wirelessService,
      Supplier<FeatureAccessConfig> featureAccess,
      ChoiceFactory choiceFactory) {
    this.plugin = plugin;
    this.customItems = customItems;
    this.wirelessService = wirelessService;
    this.featureAccess = featureAccess == null ? FeatureAccessConfig::defaults : featureAccess;
    this.choiceFactory = choiceFactory == null ? ChoiceFactory.bukkit() : choiceFactory;
  }

  public void reload() {
    unregisterAll();
    if (!RecipeRuntimeConfig.fromConfig(plugin.getConfig()).enabled()) {
      ExortLog.info("Recipes are disabled.");
      return;
    }
    File file = ensureFile();
    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
    int loaded = 0;
    int skipped = 0;

    for (RecipeSectionHandler handler : recipeSectionHandlers()) {
      ConfigurationSection section = config.getConfigurationSection(handler.name());
      if (section == null) {
        continue;
      }
      Result result = handler.loader().apply(section);
      loaded += result.loaded;
      skipped += result.skipped;
    }

    int disabled = disableRecipes(config.getStringList("disabled"));
    ExortLog.info(
        "Recipes loaded: " + loaded + ", skipped: " + skipped + ", disabled: " + disabled + ".");
  }

  public void unregisterAll() {
    for (NamespacedKey key : registered) {
      Bukkit.removeRecipe(key);
    }
    registered.clear();
    discoveryEntries.clear();
  }

  private File ensureFile() {
    File file = new File(plugin.getDataFolder(), "recipes.yml");
    if (!file.exists()) {
      plugin.saveResource("recipes.yml", false);
    }
    return file;
  }

  private List<RecipeSectionHandler> recipeSectionHandlers() {
    return List.of(
        new RecipeSectionHandler("shaped", this::registerShaped),
        new RecipeSectionHandler("shapeless", this::registerShapeless),
        new RecipeSectionHandler("smithing", this::registerSmithing),
        new RecipeSectionHandler(
            CookingRecipeType.FURNACE.section(),
            section -> registerCooking(section, CookingRecipeType.FURNACE)),
        new RecipeSectionHandler(
            CookingRecipeType.BLASTING.section(),
            section -> registerCooking(section, CookingRecipeType.BLASTING)),
        new RecipeSectionHandler(
            CookingRecipeType.SMOKING.section(),
            section -> registerCooking(section, CookingRecipeType.SMOKING)),
        new RecipeSectionHandler(
            CookingRecipeType.CAMPFIRE.section(),
            section -> registerCooking(section, CookingRecipeType.CAMPFIRE)),
        new RecipeSectionHandler("stonecutting", this::registerStonecutting));
  }

  private Result registerShaped(ConfigurationSection section) {
    int loaded = 0;
    int skipped = 0;
    for (String id : section.getKeys(false)) {
      ConfigurationSection recipe = section.getConfigurationSection(id);
      if (recipe == null) {
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"));
      if (resultItem == null) {
        skipped++;
        continue;
      }
      List<String> shape = recipe.getStringList("shape");
      if (shape == null || shape.isEmpty() || shape.size() > 3) {
        logSkip(id, "missing shape");
        skipped++;
        continue;
      }
      List<String> normalizedShape = new ArrayList<>();
      Set<Character> used = new HashSet<>();
      boolean shapeOk = true;
      int expectedWidth = -1;
      for (String line : shape) {
        if (line == null) {
          shapeOk = false;
          continue;
        }
        if (line.isEmpty() || line.length() > 3) {
          shapeOk = false;
          continue;
        }
        if (line.indexOf(' ') >= 0) {
          shapeOk = false;
          continue;
        }
        String normalized = line.replace('_', ' ');
        if (expectedWidth < 0) {
          expectedWidth = normalized.length();
        } else if (expectedWidth != normalized.length()) {
          shapeOk = false;
          continue;
        }
        normalizedShape.add(normalized);
        for (int i = 0; i < normalized.length(); i++) {
          char ch = normalized.charAt(i);
          if (ch != ' ') {
            used.add(ch);
          }
        }
      }
      if (!shapeOk || used.isEmpty()) {
        logSkip(id, "shape must be 1-3 rows of equal width and use '_' for empty slots");
        skipped++;
        continue;
      }
      NamespacedKey key = recipeKey(id);
      if (key == null) {
        logSkip(id, "invalid recipe key");
        skipped++;
        continue;
      }
      ShapedRecipe shaped = new ShapedRecipe(key, resultItem);
      try {
        shaped.shape(normalizedShape.toArray(new String[0]));
      } catch (IllegalArgumentException e) {
        logSkip(id, "invalid shape: " + e.getMessage());
        skipped++;
        continue;
      }
      ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
      if (ingredients == null) {
        logSkip(id, "missing ingredients");
        skipped++;
        continue;
      }
      boolean ok = true;
      List<String> ingredientIds = new ArrayList<>();
      Set<Character> provided = new HashSet<>();
      for (String symbol : ingredients.getKeys(false)) {
        if (symbol == null || symbol.length() != 1) {
          ok = false;
          break;
        }
        char ch = symbol.charAt(0);
        if (ch == ' ' || ch == '_') {
          continue;
        }
        String raw = ingredients.getString(symbol);
        RecipeChoice choice = resolveChoice(raw);
        if (choice == null) {
          ok = false;
          break;
        }
        try {
          shaped.setIngredient(ch, choice);
        } catch (IllegalArgumentException e) {
          ok = false;
          break;
        }
        if (used.contains(ch)) {
          ingredientIds.add(raw);
        }
        provided.add(ch);
      }
      if (ok && !provided.containsAll(used)) {
        ok = false;
        for (Character ch : used) {
          if (!provided.contains(ch)) {
            logSkip(id, "missing ingredient for symbol '" + ch + "'");
            break;
          }
        }
      }
      if (!ok) {
        logSkip(id, "invalid ingredients");
        skipped++;
        continue;
      }
      if (!applyCraftingMetadata(recipe, id, shaped)) {
        skipped++;
        continue;
      }
      if (registerRecipe(id, key, shaped)) {
        registerDiscoveryEntry(id, key, recipe, ingredientIds);
        loaded++;
      } else {
        skipped++;
      }
    }
    return new Result(loaded, skipped);
  }

  private Result registerShapeless(ConfigurationSection section) {
    int loaded = 0;
    int skipped = 0;
    for (String id : section.getKeys(false)) {
      ConfigurationSection recipe = section.getConfigurationSection(id);
      if (recipe == null) {
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"));
      if (resultItem == null) {
        skipped++;
        continue;
      }
      List<String> ingredients = recipe.getStringList("ingredients");
      if (ingredients == null || ingredients.isEmpty()) {
        logSkip(id, "missing ingredients");
        skipped++;
        continue;
      }
      if (ingredients.size() > 9) {
        logSkip(id, "too many shapeless ingredients");
        skipped++;
        continue;
      }
      NamespacedKey key = recipeKey(id);
      if (key == null) {
        logSkip(id, "invalid recipe key");
        skipped++;
        continue;
      }
      ShapelessRecipe shapeless = new ShapelessRecipe(key, resultItem);
      boolean ok = true;
      List<String> ingredientIds = new ArrayList<>();
      for (String raw : ingredients) {
        RecipeChoice choice = resolveChoice(raw);
        if (choice == null) {
          ok = false;
          break;
        }
        try {
          shapeless.addIngredient(choice);
        } catch (IllegalArgumentException e) {
          ok = false;
          break;
        }
        ingredientIds.add(raw);
      }
      if (!ok) {
        logSkip(id, "invalid ingredients");
        skipped++;
        continue;
      }
      if (!applyCraftingMetadata(recipe, id, shapeless)) {
        skipped++;
        continue;
      }
      if (registerRecipe(id, key, shapeless)) {
        registerDiscoveryEntry(id, key, recipe, ingredientIds);
        loaded++;
      } else {
        skipped++;
      }
    }
    return new Result(loaded, skipped);
  }

  private Result registerSmithing(ConfigurationSection section) {
    int loaded = 0;
    int skipped = 0;
    for (String id : section.getKeys(false)) {
      ConfigurationSection recipe = section.getConfigurationSection(id);
      if (recipe == null) {
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"));
      if (resultItem == null) {
        skipped++;
        continue;
      }
      RecipeChoice template = resolveSingleChoice(recipe, "template", id);
      RecipeChoice base = resolveSingleChoice(recipe, "base", id);
      RecipeChoice addition = resolveSingleChoice(recipe, "addition", id);
      if (template == null || base == null || addition == null) {
        skipped++;
        continue;
      }
      NamespacedKey key = recipeKey(id);
      if (key == null) {
        logSkip(id, "invalid recipe key");
        skipped++;
        continue;
      }
      SmithingTransformRecipe smithing =
          new SmithingTransformRecipe(
              key, resultItem, template, base, addition, resolveCopyDataComponents(recipe));
      if (registerRecipe(id, key, smithing)) {
        registerDiscoveryEntry(
            id,
            key,
            recipe,
            List.of(
                recipe.getString("template"),
                recipe.getString("base"),
                recipe.getString("addition")));
        loaded++;
      } else {
        skipped++;
      }
    }
    return new Result(loaded, skipped);
  }

  private Result registerCooking(ConfigurationSection section, CookingRecipeType type) {
    int loaded = 0;
    int skipped = 0;
    for (String id : section.getKeys(false)) {
      ConfigurationSection recipe = section.getConfigurationSection(id);
      if (recipe == null) {
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"));
      if (resultItem == null) {
        skipped++;
        continue;
      }
      RecipeChoice input = resolveSingleChoice(recipe, "input", id);
      if (input == null) {
        skipped++;
        continue;
      }
      float experience = resolveExperience(recipe, id);
      if (Float.isNaN(experience)) {
        skipped++;
        continue;
      }
      int cookingTime = resolveCookingTime(recipe, type.defaultCookingTime(), id);
      if (cookingTime <= 0) {
        skipped++;
        continue;
      }
      NamespacedKey key = recipeKey(id);
      if (key == null) {
        logSkip(id, "invalid recipe key");
        skipped++;
        continue;
      }
      CookingRecipe<?> cooking = type.create(key, resultItem, input, experience, cookingTime);
      if (!applyCookingMetadata(recipe, id, cooking)) {
        skipped++;
        continue;
      }
      if (registerRecipe(id, key, cooking)) {
        registerDiscoveryEntry(id, key, recipe, List.of(recipe.getString("input")));
        loaded++;
      } else {
        skipped++;
      }
    }
    return new Result(loaded, skipped);
  }

  private Result registerStonecutting(ConfigurationSection section) {
    int loaded = 0;
    int skipped = 0;
    for (String id : section.getKeys(false)) {
      ConfigurationSection recipe = section.getConfigurationSection(id);
      if (recipe == null) {
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"));
      if (resultItem == null) {
        skipped++;
        continue;
      }
      RecipeChoice input = resolveSingleChoice(recipe, "input", id);
      if (input == null) {
        skipped++;
        continue;
      }
      NamespacedKey key = recipeKey(id);
      if (key == null) {
        logSkip(id, "invalid recipe key");
        skipped++;
        continue;
      }
      StonecuttingRecipe stonecutting = new StonecuttingRecipe(key, resultItem, input);
      if (registerRecipe(id, key, stonecutting)) {
        registerDiscoveryEntry(id, key, recipe, List.of(recipe.getString("input")));
        loaded++;
      } else {
        skipped++;
      }
    }
    return new Result(loaded, skipped);
  }

  private int disableRecipes(List<String> disabled) {
    if (disabled == null || disabled.isEmpty()) return 0;
    int removed = 0;
    for (String raw : disabled) {
      if (raw == null || raw.isBlank()) continue;
      NamespacedKey key = parseNamespacedKey(raw);
      if (key == null) key = recipeKey(raw);
      if (key == null) continue;
      if (Bukkit.removeRecipe(key)) {
        NamespacedKey removedKey = key;
        registered.remove(removedKey);
        discoveryEntries.removeIf(entry -> entry.key().equals(removedKey));
        removed++;
      }
    }
    return removed;
  }

  List<RecipeDiscoveryEntry> discoveryEntries() {
    return List.copyOf(discoveryEntries);
  }

  private void registerDiscoveryEntry(
      String id, NamespacedKey key, ConfigurationSection recipe, List<String> ingredientIds) {
    List<String> unlockIds = resolveUnlockIds(recipe, ingredientIds);
    if (unlockIds.isEmpty()) {
      logDiscoverySkip(id, "no unlock triggers");
      return;
    }
    List<RecipeChoice> unlockChoices = new ArrayList<>();
    for (String raw : unlockIds) {
      RecipeChoice choice = resolveChoice(raw);
      if (choice == null) {
        logDiscoverySkip(id, "invalid unlock trigger '" + raw + "'");
        return;
      }
      unlockChoices.add(choice);
    }
    discoveryEntries.add(new RecipeDiscoveryEntry(key, unlockChoices));
  }

  List<String> resolveUnlockIds(ConfigurationSection recipe, List<String> ingredientIds) {
    Objects.requireNonNull(ingredientIds, "ingredientIds");
    if (recipe != null && recipe.contains("unlock")) {
      Object rawUnlock = recipe.get("unlock");
      if (!(rawUnlock instanceof List<?> rawList)) {
        return List.of();
      }
      List<String> result = new ArrayList<>();
      for (Object value : rawList) {
        if (!(value instanceof String raw) || raw.isBlank()) {
          return List.of();
        }
        result.add(raw.trim());
      }
      return result;
    }
    List<String> exortIngredients =
        ingredientIds.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(RecipeService::isExortIngredientId)
            .toList();
    if (!exortIngredients.isEmpty()) {
      return exortIngredients;
    }
    return ingredientIds.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(raw -> !raw.isBlank())
        .toList();
  }

  ItemStack resolveResult(ConfigurationSection section) {
    if (section == null) return null;
    String raw = section.getString("item");
    if (raw == null) return null;
    if (!allowsRecipeResult(raw)) {
      logSkip(raw, "feature is disabled");
      return null;
    }
    int amount = Math.max(1, section.getInt("amount", 1));
    ItemStack item = resolveExortItem(raw);
    if (item == null) {
      logSkip(raw, "result is not exort item");
      return null;
    }
    int maxStack = Math.max(1, item.getMaxStackSize());
    if (amount > maxStack) {
      logSkip(raw, "result amount " + amount + " exceeds max stack " + maxStack + "; clamped");
      amount = maxStack;
    }
    item.setAmount(amount);
    return item;
  }

  RecipeChoice resolveSingleChoice(ConfigurationSection section, String field, String recipeId) {
    if (section == null) {
      logSkip(recipeId, "missing recipe section");
      return null;
    }
    String raw = section.getString(field);
    if (raw == null || raw.isBlank()) {
      logSkip(recipeId, "missing " + field);
      return null;
    }
    RecipeChoice choice = resolveChoice(raw);
    if (choice == null) {
      logSkip(recipeId, "invalid " + field + " ingredient");
    }
    return choice;
  }

  float resolveExperience(ConfigurationSection section, String recipeId) {
    Object raw = section == null ? null : section.get("experience");
    double experience;
    if (raw == null) {
      experience = 0.0D;
    } else if (raw instanceof Number number) {
      experience = number.doubleValue();
    } else {
      logSkip(recipeId, "experience must be a non-negative finite number");
      return Float.NaN;
    }
    if (!Double.isFinite(experience) || experience < 0.0D || experience > Float.MAX_VALUE) {
      logSkip(recipeId, "experience must be a non-negative finite number");
      return Float.NaN;
    }
    return (float) experience;
  }

  int resolveCookingTime(ConfigurationSection section, int defaultTicks, String recipeId) {
    Object raw = section == null ? null : section.get("cookingTime");
    double cookingTime;
    if (raw == null) {
      cookingTime = defaultTicks;
    } else if (raw instanceof Number number) {
      cookingTime = number.doubleValue();
    } else {
      logSkip(recipeId, "cookingTime must be a positive integer");
      return -1;
    }
    if (!Double.isFinite(cookingTime)
        || cookingTime <= 0.0D
        || cookingTime > Integer.MAX_VALUE
        || cookingTime % 1.0D != 0.0D) {
      logSkip(recipeId, "cookingTime must be a positive integer");
      return -1;
    }
    return (int) cookingTime;
  }

  private boolean applyCraftingMetadata(
      ConfigurationSection section, String recipeId, CraftingRecipe recipe) {
    String group = section.getString("group");
    if (group != null && !group.isBlank()) {
      recipe.setGroup(group.trim());
    }
    CraftingBookCategory category = parseCraftingCategory(section.getString("category"));
    if (category == null) {
      logSkip(recipeId, "unknown crafting category '" + section.getString("category") + "'");
      return false;
    }
    recipe.setCategory(category);
    return true;
  }

  private boolean applyCookingMetadata(
      ConfigurationSection section, String recipeId, CookingRecipe<?> recipe) {
    String group = section.getString("group");
    if (group != null && !group.isBlank()) {
      recipe.setGroup(group.trim());
    }
    CookingBookCategory category = parseCookingCategory(section.getString("category"));
    if (category == null) {
      logSkip(recipeId, "unknown cooking category '" + section.getString("category") + "'");
      return false;
    }
    recipe.setCategory(category);
    return true;
  }

  static CraftingBookCategory parseCraftingCategory(String raw) {
    if (raw == null || raw.isBlank()) {
      return CraftingBookCategory.MISC;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "building" -> CraftingBookCategory.BUILDING;
      case "redstone" -> CraftingBookCategory.REDSTONE;
      case "equipment" -> CraftingBookCategory.EQUIPMENT;
      case "misc" -> CraftingBookCategory.MISC;
      default -> null;
    };
  }

  static CookingBookCategory parseCookingCategory(String raw) {
    if (raw == null || raw.isBlank()) {
      return CookingBookCategory.MISC;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "food" -> CookingBookCategory.FOOD;
      case "blocks" -> CookingBookCategory.BLOCKS;
      case "misc" -> CookingBookCategory.MISC;
      default -> null;
    };
  }

  static boolean resolveCopyDataComponents(ConfigurationSection section) {
    return section != null && section.getBoolean("copyDataComponents", false);
  }

  static int defaultCookingTime(String sectionName) {
    CookingRecipeType type = CookingRecipeType.fromSection(sectionName);
    return type == null ? -1 : type.defaultCookingTime();
  }

  private NamespacedKey recipeKey(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT), plugin);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private boolean registerRecipe(String id, NamespacedKey key, Recipe recipe) {
    try {
      if (!Bukkit.addRecipe(recipe)) {
        logSkip(id, "Bukkit rejected recipe");
        return false;
      }
      registered.add(key);
      return true;
    } catch (IllegalArgumentException | IllegalStateException e) {
      logSkip(id, "failed to register recipe: " + e.getMessage());
      return false;
    }
  }

  RecipeChoice resolveChoice(String raw) {
    if (raw == null) return null;
    String id = raw.trim();
    if (id.isEmpty()) return null;
    if (id.startsWith("#")) {
      NamespacedKey key = parseNamespacedKey(id.substring(1));
      if (key == null) return null;
      Tag<Material> tag = Bukkit.getTag("items", key, Material.class);
      if (tag == null) return null;
      return materialChoice(tag.getValues());
    }
    if (id.toLowerCase(Locale.ROOT).startsWith("exort:")) {
      ItemStack exortItem = resolveExortItem(id);
      if (exortItem == null) return null;
      return exactChoice(exortItem);
    }
    Material material = resolveMaterial(id);
    if (material == null) return null;
    return materialChoice(material);
  }

  RecipeChoice materialChoice(Material material) {
    return choiceFactory.material(material);
  }

  RecipeChoice materialChoice(Collection<Material> materials) {
    return choiceFactory.materials(materials);
  }

  RecipeChoice exactChoice(ItemStack item) {
    return choiceFactory.exact(item);
  }

  interface ChoiceFactory {
    RecipeChoice material(Material material);

    RecipeChoice materials(Collection<Material> materials);

    RecipeChoice exact(ItemStack item);

    static ChoiceFactory bukkit() {
      return new ChoiceFactory() {
        @Override
        public RecipeChoice material(Material material) {
          return new RecipeChoice.MaterialChoice(material);
        }

        @Override
        public RecipeChoice materials(Collection<Material> materials) {
          return new RecipeChoice.MaterialChoice(materials.toArray(new Material[0]));
        }

        @Override
        public RecipeChoice exact(ItemStack item) {
          return new RecipeChoice.ExactChoice(item);
        }
      };
    }
  }

  private Material resolveMaterial(String raw) {
    String id = raw.trim();
    if (id.contains(":")) {
      NamespacedKey key = parseNamespacedKey(id);
      if (key != null && "minecraft".equalsIgnoreCase(key.getNamespace())) {
        id = key.getKey();
      }
    }
    return Material.matchMaterial(id);
  }

  static NamespacedKey parseNamespacedKey(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  ItemStack resolveExortItem(String raw) {
    if (raw == null) return null;
    String id = raw.trim().toLowerCase(Locale.ROOT);
    if (id.startsWith("exort:")) {
      id = id.substring("exort:".length());
    }
    return switch (id) {
      case "wire" -> customItems.wireItem();
      case "terminal" -> customItems.terminalItem();
      case "crafting_terminal" -> customItems.craftingTerminalItem();
      case "monitor" -> customItems.monitorItem();
      case "import_bus" -> customItems.importBusItem();
      case "export_bus" -> customItems.exportBusItem();
      case "relay" -> customItems.relayItem();
      case "chunk_loader" -> customItems.chunkLoaderItem(ChunkLoaderType.CHUNK_LOADER);
      case "personal_chunk_loader" ->
          customItems.chunkLoaderItem(ChunkLoaderType.PERSONAL_CHUNK_LOADER);
      case "dormant_chunk_loader" ->
          customItems.chunkLoaderItem(ChunkLoaderType.DORMANT_CHUNK_LOADER);
      case "wireless_terminal" ->
          wirelessService != null
              ? wirelessService.create()
              : customItems.wirelessTerminalItem(null, 100);
      case "storage_core" -> customItems.storageCoreItem();
      default -> {
        if (id.startsWith("storage:")) {
          String tier = id.substring("storage:".length());
          var tierOpt = StorageTier.fromString(tier);
          yield tierOpt.map(t -> customItems.storageItem(t, null)).orElse(null);
        }
        yield null;
      }
    };
  }

  boolean allowsRecipeResult(String raw) {
    FeatureAccessConfig access = featureAccess.get();
    if (access == null) {
      access = FeatureAccessConfig.defaults();
    }
    return access.allowsRecipeResult(raw);
  }

  private void logSkip(String id, String reason) {
    ExortLog.warn("Skipped recipe '" + id + "': " + reason);
  }

  private void logDiscoverySkip(String id, String reason) {
    ExortLog.warn("Recipe discovery disabled for '" + id + "': " + reason);
  }

  private static boolean isExortIngredientId(String raw) {
    return raw != null && raw.trim().toLowerCase(Locale.ROOT).startsWith("exort:");
  }

  private record RecipeSectionHandler(String name, Function<ConfigurationSection, Result> loader) {}

  private record Result(int loaded, int skipped) {}

  private enum CookingRecipeType {
    FURNACE("furnace", 200) {
      @Override
      CookingRecipe<?> create(
          NamespacedKey key, ItemStack result, RecipeChoice input, float experience, int time) {
        return new FurnaceRecipe(key, result, input, experience, time);
      }
    },
    BLASTING("blasting", 100) {
      @Override
      CookingRecipe<?> create(
          NamespacedKey key, ItemStack result, RecipeChoice input, float experience, int time) {
        return new BlastingRecipe(key, result, input, experience, time);
      }
    },
    SMOKING("smoking", 100) {
      @Override
      CookingRecipe<?> create(
          NamespacedKey key, ItemStack result, RecipeChoice input, float experience, int time) {
        return new SmokingRecipe(key, result, input, experience, time);
      }
    },
    CAMPFIRE("campfire", 600) {
      @Override
      CookingRecipe<?> create(
          NamespacedKey key, ItemStack result, RecipeChoice input, float experience, int time) {
        return new CampfireRecipe(key, result, input, experience, time);
      }
    };

    private final String section;
    private final int defaultCookingTime;

    CookingRecipeType(String section, int defaultCookingTime) {
      this.section = section;
      this.defaultCookingTime = defaultCookingTime;
    }

    private String section() {
      return section;
    }

    private int defaultCookingTime() {
      return defaultCookingTime;
    }

    private static CookingRecipeType fromSection(String section) {
      if (section == null) {
        return null;
      }
      String normalized = section.trim().toLowerCase(Locale.ROOT);
      for (CookingRecipeType type : values()) {
        if (type.section.equals(normalized)) {
          return type;
        }
      }
      return null;
    }

    abstract CookingRecipe<?> create(
        NamespacedKey key, ItemStack result, RecipeChoice input, float experience, int time);
  }
}
