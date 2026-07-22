package com.zxcmc.exort.recipes;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.infra.config.FeatureAccessConfig;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
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
  private final RecipeRegistry recipeRegistry;
  private final Function<String, java.util.Optional<StorageTier>> tierResolver;
  private final List<NamespacedKey> registered = new ArrayList<>();
  private final List<RecipeDiscoveryEntry> discoveryEntries = new ArrayList<>();
  private List<RegisteredRecipe> disabledOriginals = List.of();
  private boolean registrationFailed;
  private boolean validationMode;
  private boolean validationFailed;
  private boolean applyingCandidate;
  private Set<NamespacedKey> validationKeys = Set.of();

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

  public RecipeService(
      JavaPlugin plugin,
      CustomItems customItems,
      WirelessTerminalService wirelessService,
      Function<String, java.util.Optional<StorageTier>> tierResolver) {
    this(
        plugin,
        customItems,
        wirelessService,
        () ->
            plugin == null
                ? FeatureAccessConfig.defaults()
                : FeatureAccessConfig.fromConfig(plugin.getConfig()),
        ChoiceFactory.bukkit(),
        RecipeRegistry.bukkit(),
        tierResolver);
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
    this(
        plugin,
        customItems,
        wirelessService,
        featureAccess,
        choiceFactory,
        RecipeRegistry.bukkit());
  }

  RecipeService(
      JavaPlugin plugin,
      CustomItems customItems,
      WirelessTerminalService wirelessService,
      Supplier<FeatureAccessConfig> featureAccess,
      ChoiceFactory choiceFactory,
      RecipeRegistry recipeRegistry) {
    this(
        plugin,
        customItems,
        wirelessService,
        featureAccess,
        choiceFactory,
        recipeRegistry,
        ignored -> java.util.Optional.empty());
  }

  private RecipeService(
      JavaPlugin plugin,
      CustomItems customItems,
      WirelessTerminalService wirelessService,
      Supplier<FeatureAccessConfig> featureAccess,
      ChoiceFactory choiceFactory,
      RecipeRegistry recipeRegistry,
      Function<String, java.util.Optional<StorageTier>> tierResolver) {
    this.plugin = plugin;
    this.customItems = customItems;
    this.wirelessService = wirelessService;
    this.featureAccess = featureAccess == null ? FeatureAccessConfig::defaults : featureAccess;
    this.choiceFactory = choiceFactory == null ? ChoiceFactory.bukkit() : choiceFactory;
    this.recipeRegistry = Objects.requireNonNull(recipeRegistry, "recipeRegistry");
    this.tierResolver = Objects.requireNonNull(tierResolver, "tierResolver");
  }

  public void reload() {
    reloadReplacing(this);
  }

  /** Loads an immutable recipe-file snapshot before destructive runtime activation begins. */
  public static Activation prepare(JavaPlugin plugin, ConfigurationSection runtimeConfig) {
    Objects.requireNonNull(plugin, "plugin");
    File file = new File(plugin.getDataFolder(), "recipes.yml");
    if (!file.isFile()) {
      throw new IllegalStateException("recipes.yml does not exist");
    }
    YamlConfiguration config = new YamlConfiguration();
    try {
      config.load(file);
    } catch (Exception error) {
      throw new IllegalStateException("recipes.yml is invalid", error);
    }
    boolean enabled = RecipeRuntimeConfig.fromConfig(runtimeConfig).enabled();
    return Activation.candidate(config.saveToString(), enabled);
  }

  /** Captures the exact registry objects owned or disabled by this generation. */
  public Checkpoint checkpoint() {
    return snapshot();
  }

  /** Activates either a prepared candidate or an exact prior checkpoint without rereading files. */
  public void activate(Activation activation) {
    Objects.requireNonNull(activation, "activation");
    if (activation.checkpoint != null) {
      restore(activation.checkpoint);
      return;
    }
    YamlConfiguration config = new YamlConfiguration();
    try {
      config.loadFromString(activation.serializedConfig);
    } catch (Exception error) {
      throw new IllegalStateException("Prepared recipes.yml snapshot is invalid", error);
    }
    if (!reloadReplacing(null, config, activation.enabled)) {
      throw new IllegalStateException("Prepared recipe activation failed");
    }
  }

  /** Semantically validates a prepared candidate without touching the live recipe registry. */
  public boolean validateActivation(Activation activation) {
    Objects.requireNonNull(activation, "activation");
    if (activation.checkpoint != null || !activation.enabled) {
      return true;
    }
    YamlConfiguration config = new YamlConfiguration();
    try {
      config.loadFromString(activation.serializedConfig);
    } catch (Exception error) {
      return false;
    }
    return validateRecipeConfig(config);
  }

  public boolean reloadReplacing(RecipeService previous) {
    YamlConfiguration config = new YamlConfiguration();
    try {
      File file = ensureFile();
      config.load(file);
    } catch (Exception e) {
      ExortLog.error(
          "Failed to load recipes.yml; keeping the last-known-good recipes: " + e.getMessage());
      return false;
    }
    return reloadReplacing(
        previous, config, RecipeRuntimeConfig.fromConfig(plugin.getConfig()).enabled());
  }

  boolean reloadReplacing(RecipeService previous, YamlConfiguration config, boolean enabled) {
    Objects.requireNonNull(config, "config");
    if (!enabled) {
      if (previous != null) {
        previous.unregisterAll();
      } else {
        unregisterAll();
      }
      ExortLog.info("Recipes are disabled.");
      return true;
    }
    if (!validateRecipeConfig(config)) {
      ExortLog.error(
          "Failed to validate recipes.yml; keeping the last-known-good recipe set unchanged.");
      return false;
    }
    Checkpoint previousSnapshot;
    List<RegisteredRecipe> disabledRecipeSnapshot;
    try {
      previousSnapshot = previous == null ? Checkpoint.empty() : previous.snapshot();
      Set<NamespacedKey> previousKeys =
          previous == null ? Set.of() : Set.copyOf(previous.registered);
      disabledRecipeSnapshot =
          snapshotConfiguredRecipes(config.getStringList("disabled"), previousKeys);
    } catch (RuntimeException e) {
      ExortLog.error(
          "Failed to snapshot the current recipe set; keeping it unchanged: " + e.getMessage());
      return false;
    }
    if (previous != null) {
      previous.unregisterAll();
    } else {
      unregisterAll();
    }
    registrationFailed = false;
    ApplyResult applied;
    try {
      applyingCandidate = true;
      applied = applyRecipeConfig(config);
    } catch (RuntimeException e) {
      registrationFailed = true;
      ExortLog.error("Failed to apply recipes.yml: " + e.getMessage());
      applied = new ApplyResult(0, 0, 0);
    } finally {
      applyingCandidate = false;
    }
    if (registrationFailed) {
      unregisterAll();
      if (previous != null) {
        previous.restore(previousSnapshot);
      }
      restoreMissing(disabledRecipeSnapshot);
      ExortLog.error("Recipe reload was rolled back to the last-known-good recipe set.");
      return false;
    }
    disabledOriginals = List.copyOf(disabledRecipeSnapshot);
    ExortLog.info(
        "Recipes loaded: "
            + applied.loaded()
            + ", skipped: "
            + applied.skipped()
            + ", disabled: "
            + applied.disabled()
            + ".");
    return true;
  }

  boolean validateRecipeConfig(YamlConfiguration config) {
    Objects.requireNonNull(config, "config");
    if (validationMode) {
      throw new IllegalStateException("Recipe validation is already running");
    }
    validationMode = true;
    validationFailed = false;
    validationKeys = new HashSet<>();
    try {
      applyRecipeConfig(config);
      return !validationFailed;
    } catch (RuntimeException e) {
      ExortLog.error("Failed to validate recipes.yml: " + e.getMessage());
      return false;
    } finally {
      validationMode = false;
      validationKeys = Set.of();
    }
  }

  private ApplyResult applyRecipeConfig(YamlConfiguration config) {
    int loaded = 0;
    int skipped = 0;
    for (RecipeSectionHandler handler : recipeSectionHandlers()) {
      ConfigurationSection section = config.getConfigurationSection(handler.name());
      if (section == null) {
        if (config.contains(handler.name())) {
          logSkip(handler.name(), "recipe group must be a configuration section");
          skipped++;
        }
        continue;
      }
      Result result = handler.loader().apply(section);
      loaded += result.loaded;
      skipped += result.skipped;
    }
    int disabled =
        !validationMode && registrationFailed
            ? 0
            : disableRecipes(config.getStringList("disabled"));
    return new ApplyResult(loaded, skipped, disabled);
  }

  public void unregisterAll() {
    for (NamespacedKey key : registered) {
      recipeRegistry.remove(key);
    }
    registered.clear();
    discoveryEntries.clear();
    restoreMissing(disabledOriginals);
    disabledOriginals = List.of();
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
        logSkip(id, "recipe must be a configuration section");
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"), id);
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
        logSkip(id, "recipe must be a configuration section");
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"), id);
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
        logSkip(id, "recipe must be a configuration section");
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"), id);
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
        logSkip(id, "recipe must be a configuration section");
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"), id);
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
        logSkip(id, "recipe must be a configuration section");
        skipped++;
        continue;
      }
      ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"), id);
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
      if (key == null) {
        logSkip(raw, "invalid disabled recipe key");
        continue;
      }
      if (validationMode) {
        continue;
      }
      if (recipeRegistry.remove(key)) {
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
    if (!validationMode) {
      discoveryEntries.add(new RecipeDiscoveryEntry(key, unlockChoices));
    }
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
    return resolveResult(section, "<unknown>");
  }

  private ItemStack resolveResult(ConfigurationSection section, String recipeId) {
    if (section == null) {
      logSkip(recipeId, "missing result section");
      return null;
    }
    String raw = section.getString("item");
    if (raw == null || raw.isBlank()) {
      logSkip(recipeId, "missing result item");
      return null;
    }
    if (!allowsRecipeResult(raw)) {
      logAllowedSkip(raw, "feature is disabled");
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
    String normalized = raw.toLowerCase(Locale.ROOT);
    try {
      if (normalized.indexOf(':') >= 0) {
        return NamespacedKey.fromString(normalized);
      }
      return plugin == null
          ? NamespacedKey.fromString("exort:" + normalized)
          : NamespacedKey.fromString(normalized, plugin);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private boolean registerRecipe(String id, NamespacedKey key, Recipe recipe) {
    if (validationMode) {
      if (!validationKeys.add(key)) {
        logSkip(id, "duplicate recipe key '" + key + "'");
        return false;
      }
      return true;
    }
    try {
      if (!recipeRegistry.add(recipe)) {
        logSkip(id, "Bukkit rejected recipe");
        registrationFailed = true;
        return false;
      }
      registered.add(key);
      return true;
    } catch (IllegalArgumentException | IllegalStateException e) {
      logSkip(id, "failed to register recipe: " + e.getMessage());
      registrationFailed = true;
      return false;
    }
  }

  private Checkpoint snapshot() {
    List<RegisteredRecipe> recipes = new ArrayList<>();
    for (NamespacedKey key : registered) {
      Recipe recipe = recipeRegistry.get(key);
      if (recipe != null) {
        recipes.add(new RegisteredRecipe(key, recipe));
      }
    }
    return new Checkpoint(
        List.copyOf(recipes), List.copyOf(discoveryEntries), List.copyOf(disabledOriginals));
  }

  private List<RegisteredRecipe> snapshotConfiguredRecipes(
      List<String> configuredKeys, Set<NamespacedKey> excludedKeys) {
    if (configuredKeys == null || configuredKeys.isEmpty()) {
      return List.of();
    }
    List<RegisteredRecipe> recipes = new ArrayList<>();
    Set<NamespacedKey> seen = new HashSet<>();
    for (String raw : configuredKeys) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      NamespacedKey key = parseNamespacedKey(raw);
      if (key == null) {
        key = recipeKey(raw);
      }
      if (key == null || excludedKeys.contains(key) || !seen.add(key)) {
        continue;
      }
      Recipe recipe = recipeRegistry.get(key);
      if (recipe != null) {
        recipes.add(new RegisteredRecipe(key, recipe));
      }
    }
    return List.copyOf(recipes);
  }

  private void restore(Checkpoint snapshot) {
    registered.clear();
    discoveryEntries.clear();
    disabledOriginals = List.of();
    if (snapshot == null) {
      return;
    }
    for (RegisteredRecipe entry : snapshot.disabledOriginals) {
      recipeRegistry.remove(entry.key());
    }
    for (RegisteredRecipe entry : snapshot.recipes()) {
      try {
        if (recipeRegistry.add(entry.recipe())) {
          registered.add(entry.key());
        } else {
          ExortLog.error("Failed to restore recipe '" + entry.key() + "' after reload rollback.");
        }
      } catch (IllegalArgumentException | IllegalStateException e) {
        ExortLog.error(
            "Failed to restore recipe '"
                + entry.key()
                + "' after reload rollback: "
                + e.getMessage());
      }
    }
    discoveryEntries.addAll(snapshot.discoveryEntries());
    disabledOriginals = List.copyOf(snapshot.disabledOriginals);
  }

  private void restoreMissing(List<RegisteredRecipe> recipes) {
    if (recipes == null || recipes.isEmpty()) {
      return;
    }
    for (RegisteredRecipe entry : recipes) {
      try {
        if (recipeRegistry.get(entry.key()) != null) {
          continue;
        }
        if (!recipeRegistry.add(entry.recipe())) {
          ExortLog.error(
              "Failed to restore disabled recipe '" + entry.key() + "' after reload rollback.");
        }
      } catch (IllegalArgumentException | IllegalStateException e) {
        ExortLog.error(
            "Failed to restore disabled recipe '"
                + entry.key()
                + "' after reload rollback: "
                + e.getMessage());
      }
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
      case "transmitter" -> customItems.transmitterItem();
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
          var tierOpt = tierResolver.apply(tier);
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
    if (validationMode) {
      validationFailed = true;
    } else if (applyingCandidate) {
      registrationFailed = true;
    }
    ExortLog.warn("Skipped recipe '" + id + "': " + reason);
  }

  private void logAllowedSkip(String id, String reason) {
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

  private record ApplyResult(int loaded, int skipped, int disabled) {}

  private record RegisteredRecipe(NamespacedKey key, Recipe recipe) {}

  public static final class Activation {
    private final String serializedConfig;
    private final boolean enabled;
    private final Checkpoint checkpoint;

    private Activation(String serializedConfig, boolean enabled, Checkpoint checkpoint) {
      this.serializedConfig = serializedConfig;
      this.enabled = enabled;
      this.checkpoint = checkpoint;
    }

    public static Activation candidate(String serializedConfig, boolean enabled) {
      return new Activation(
          Objects.requireNonNull(serializedConfig, "serializedConfig"), enabled, null);
    }

    public static Activation restore(Checkpoint checkpoint) {
      return new Activation(null, true, Objects.requireNonNull(checkpoint, "checkpoint"));
    }
  }

  public static final class Checkpoint {
    private final List<RegisteredRecipe> recipes;
    private final List<RecipeDiscoveryEntry> discoveryEntries;
    private final List<RegisteredRecipe> disabledOriginals;

    private Checkpoint(
        List<RegisteredRecipe> recipes,
        List<RecipeDiscoveryEntry> discoveryEntries,
        List<RegisteredRecipe> disabledOriginals) {
      this.recipes = recipes;
      this.discoveryEntries = discoveryEntries;
      this.disabledOriginals = disabledOriginals;
    }

    private List<RegisteredRecipe> recipes() {
      return recipes;
    }

    private List<RecipeDiscoveryEntry> discoveryEntries() {
      return discoveryEntries;
    }

    private static Checkpoint empty() {
      return new Checkpoint(List.of(), List.of(), List.of());
    }

    /** Stable semantic fingerprint of Exort recipes, discovery entries, and disabled originals. */
    public String fingerprint() {
      StringBuilder canonical = new StringBuilder();
      appendRecipes(canonical, "registered", recipes);
      canonical.append("discovery\n");
      discoveryEntries.stream()
          .sorted(java.util.Comparator.comparing(entry -> entry.key().toString()))
          .forEach(
              entry -> {
                canonical.append(entry.key()).append('|');
                entry.unlockChoices().stream()
                    .map(Checkpoint::choiceFingerprint)
                    .sorted()
                    .forEach(choice -> canonical.append(choice).append(';'));
                canonical.append('\n');
              });
      appendRecipes(canonical, "disabled", disabledOriginals);
      try {
        return HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
      } catch (NoSuchAlgorithmException impossible) {
        throw new IllegalStateException("SHA-256 is unavailable", impossible);
      }
    }

    private static void appendRecipes(
        StringBuilder canonical, String label, List<RegisteredRecipe> entries) {
      canonical.append(label).append('\n');
      entries.stream()
          .sorted(java.util.Comparator.comparing(entry -> entry.key().toString()))
          .forEach(
              entry ->
                  canonical
                      .append(entry.key())
                      .append('|')
                      .append(recipeFingerprint(entry.recipe()))
                      .append('\n'));
    }

    private static String recipeFingerprint(Recipe recipe) {
      StringBuilder value =
          new StringBuilder(recipe.getClass().getName())
              .append('|')
              .append(itemFingerprint(recipe.getResult()));
      if (recipe instanceof Keyed keyed) {
        value.append('|').append(keyed.getKey());
      }
      if (recipe instanceof ShapedRecipe shaped) {
        value.append("|shape=").append(String.join("/", shaped.getShape()));
        shaped.getChoiceMap().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(
                entry ->
                    value
                        .append('|')
                        .append(entry.getKey())
                        .append('=')
                        .append(choiceFingerprint(entry.getValue())));
      } else if (recipe instanceof ShapelessRecipe shapeless) {
        value.append("|choices=");
        shapeless.getChoiceList().stream()
            .map(Checkpoint::choiceFingerprint)
            .sorted()
            .forEach(choice -> value.append(choice).append(';'));
      } else if (recipe instanceof CookingRecipe<?> cooking) {
        value
            .append("|input=")
            .append(choiceFingerprint(cooking.getInputChoice()))
            .append("|experience=")
            .append(cooking.getExperience())
            .append("|time=")
            .append(cooking.getCookingTime());
      } else if (recipe instanceof StonecuttingRecipe stonecutting) {
        value.append("|input=").append(choiceFingerprint(stonecutting.getInputChoice()));
      } else if (recipe instanceof SmithingTransformRecipe smithing) {
        value
            .append("|template=")
            .append(choiceFingerprint(smithing.getTemplate()))
            .append("|base=")
            .append(choiceFingerprint(smithing.getBase()))
            .append("|addition=")
            .append(choiceFingerprint(smithing.getAddition()))
            .append("|copy=")
            .append(smithing.willCopyDataComponents());
      }
      return value.toString();
    }

    private static String choiceFingerprint(RecipeChoice choice) {
      if (choice == null) {
        return "null";
      }
      if (choice instanceof RecipeChoice.MaterialChoice materials) {
        return materials.getChoices().stream()
            .map(material -> material.getKey().toString())
            .sorted()
            .collect(java.util.stream.Collectors.joining(",", "materials[", "]"));
      }
      if (choice instanceof RecipeChoice.ExactChoice exact) {
        return exact.getChoices().stream()
            .map(Checkpoint::itemFingerprint)
            .sorted()
            .collect(java.util.stream.Collectors.joining(",", "exact[", "]"));
      }
      return choice.getClass().getName() + ':' + choice;
    }

    private static String itemFingerprint(ItemStack item) {
      if (item == null) {
        return "null";
      }
      return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
  }

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
