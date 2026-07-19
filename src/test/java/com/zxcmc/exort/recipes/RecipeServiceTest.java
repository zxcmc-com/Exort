package com.zxcmc.exort.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.infra.config.FeatureAccessConfig;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecipeServiceTest {
  @BeforeAll
  static void installBukkitServer() {
    BukkitTestDoubles.world("recipe-service", new UUID(0L, 1808L));
  }

  @Test
  void parseNamespacedKeyReturnsNullForBlankOrMalformedInput() {
    assertNull(RecipeService.parseNamespacedKey(null));
    assertNull(RecipeService.parseNamespacedKey(" "));
    assertNull(RecipeService.parseNamespacedKey("bad key"));
    assertNull(RecipeService.parseNamespacedKey("minecraft:bad key"));
  }

  @Test
  void parseNamespacedKeyNormalizesUppercaseInput() {
    var key = RecipeService.parseNamespacedKey("Minecraft:Stone");

    assertNotNull(key);
    assertEquals("minecraft", key.getNamespace());
    assertEquals("stone", key.getKey());
  }

  @Test
  void resolvesChunkLoaderFixedIdsAndRejectsOldVariantIds() {
    RecordingCustomItems customItems = new RecordingCustomItems();
    RecipeService service = new RecipeService(null, customItems, null);

    assertNotNull(service.resolveExortItem("exort:chunk_loader"));
    assertEquals(ChunkLoaderType.CHUNK_LOADER, customItems.lastType);

    assertNotNull(service.resolveExortItem("personal_chunk_loader"));
    assertEquals(ChunkLoaderType.PERSONAL_CHUNK_LOADER, customItems.lastType);

    assertNotNull(service.resolveExortItem("dormant_chunk_loader"));
    assertEquals(ChunkLoaderType.DORMANT_CHUNK_LOADER, customItems.lastType);

    assertNull(service.resolveExortItem("exort:chunk_loader:personal"));
  }

  @Test
  void featureFlagsRejectDisabledRecipeResultsWithoutDisablingItemFactories() {
    RecordingCustomItems customItems = new RecordingCustomItems();
    RecipeService service =
        new RecipeService(
            null, customItems, null, () -> new FeatureAccessConfig(false, false, false));

    assertFalse(service.allowsRecipeResult("exort:relay"));
    assertFalse(service.allowsRecipeResult("chunk_loader"));
    assertFalse(service.allowsRecipeResult("personal_chunk_loader"));
    assertFalse(service.allowsRecipeResult("dormant_chunk_loader"));
    assertFalse(service.allowsRecipeResult("transmitter"));
    assertFalse(service.allowsRecipeResult("wireless_terminal"));
    assertNotNull(service.resolveExortItem("transmitter"));
    assertNotNull(service.resolveExortItem("chunk_loader"));
  }

  @Test
  void defaultCookingTimesMatchRecipeType() {
    assertEquals(200, RecipeService.defaultCookingTime("furnace"));
    assertEquals(100, RecipeService.defaultCookingTime("blasting"));
    assertEquals(100, RecipeService.defaultCookingTime("smoking"));
    assertEquals(600, RecipeService.defaultCookingTime("campfire"));
    assertEquals(-1, RecipeService.defaultCookingTime("unknown"));
  }

  @Test
  void unknownRecipeBookCategoryIsRejected() {
    assertEquals(CraftingBookCategory.MISC, RecipeService.parseCraftingCategory(null));
    assertEquals(CraftingBookCategory.REDSTONE, RecipeService.parseCraftingCategory("redstone"));
    assertNull(RecipeService.parseCraftingCategory("magic"));

    assertEquals(CookingBookCategory.MISC, RecipeService.parseCookingCategory(null));
    assertEquals(CookingBookCategory.BLOCKS, RecipeService.parseCookingCategory("blocks"));
    assertNull(RecipeService.parseCookingCategory("equipment"));
  }

  @Test
  void missingCookingInputChoiceIsRejected() {
    RecipeService service = new RecipeService(null, new RecordingCustomItems(), null);
    ConfigurationSection recipe = new YamlConfiguration().createSection("recipe");

    assertNull(service.resolveSingleChoice(recipe, "input", "test_recipe"));
  }

  @Test
  void nonExortResultIsRejected() {
    RecipeService service = new RecipeService(null, new RecordingCustomItems(), null);
    ConfigurationSection result = new YamlConfiguration().createSection("result");
    result.set("item", "minecraft:stone");

    assertNull(service.resolveResult(result));
  }

  @Test
  void smithingCopyDataComponentsDefaultsToFalse() {
    ConfigurationSection recipe = new YamlConfiguration().createSection("recipe");

    assertFalse(RecipeService.resolveCopyDataComponents(recipe));

    recipe.set("copyDataComponents", true);
    assertTrue(RecipeService.resolveCopyDataComponents(recipe));
  }

  @Test
  void resolvesExplicitUnlockIdsAndFallbacks() {
    RecipeService service = new RecipeService(null, new RecordingCustomItems(), null);
    ConfigurationSection recipe = new YamlConfiguration().createSection("recipe");
    recipe.set("unlock", List.of("minecraft:redstone", "exort:wire"));

    assertEquals(
        List.of("minecraft:redstone", "exort:wire"),
        service.resolveUnlockIds(recipe, List.of("minecraft:iron_ingot")));

    ConfigurationSection fallback = new YamlConfiguration().createSection("fallback");
    assertEquals(
        List.of("exort:wire"),
        service.resolveUnlockIds(fallback, List.of("minecraft:redstone", "exort:wire")));
    assertEquals(
        List.of("minecraft:redstone", "minecraft:glass"),
        service.resolveUnlockIds(fallback, List.of("minecraft:redstone", "minecraft:glass")));

    fallback.set("unlock", "minecraft:redstone");
    assertEquals(List.of(), service.resolveUnlockIds(fallback, List.of("minecraft:redstone")));
  }

  @Test
  void resolvesUnlockChoicesForMaterialsTagsAndExactExortItems() {
    RecordingCustomItems customItems = new RecordingCustomItems();
    RecipeService service =
        new RecipeService(
            null, customItems, null, FeatureAccessConfig::defaults, testChoiceFactory());

    RecipeChoice material = service.resolveChoice("minecraft:redstone");
    assertNotNull(material);
    assertTrue(material.test(new MarkerItemStack(Material.REDSTONE, null)));

    RecipeChoice shortMaterial = service.resolveChoice("redstone");
    assertNotNull(shortMaterial);
    assertTrue(shortMaterial.test(new MarkerItemStack(Material.REDSTONE, null)));

    RecipeChoice tag = service.resolveChoice("#minecraft:planks");
    assertNotNull(tag);
    assertTrue(tag.test(new MarkerItemStack(Material.OAK_PLANKS, null)));
    assertFalse(tag.test(new MarkerItemStack(Material.STONE, null)));

    RecipeChoice exort = service.resolveChoice("exort:wire");
    assertNotNull(exort);
    assertTrue(exort.test(new MarkerItemStack(Material.PAPER, "wire")));
    assertFalse(exort.test(new MarkerItemStack(Material.PAPER, null)));
  }

  @Test
  void discoveryEntryMatchesVanillaTagAndExactExortProgressionItems() {
    RecipeDiscoveryEntry vanillaEntry =
        new RecipeDiscoveryEntry(
            NamespacedKey.minecraft("test_redstone"),
            List.of(materialTriggerChoice(Material.REDSTONE)));
    assertTrue(
        vanillaEntry.matchesAny(new ItemStack[] {new MarkerItemStack(Material.REDSTONE, null)}));
    assertFalse(
        vanillaEntry.matchesAny(new ItemStack[] {new MarkerItemStack(Material.IRON_INGOT, null)}));

    RecipeDiscoveryEntry tagEntry =
        new RecipeDiscoveryEntry(
            NamespacedKey.minecraft("test_planks"),
            List.of(materialTriggerChoice(Material.OAK_PLANKS, Material.SPRUCE_PLANKS)));
    assertTrue(tagEntry.matches(new MarkerItemStack(Material.SPRUCE_PLANKS, null)));

    RecipeDiscoveryEntry exortEntry =
        new RecipeDiscoveryEntry(
            NamespacedKey.minecraft("test_exort_wire"),
            List.of(exactTriggerChoice(new MarkerItemStack(Material.PAPER, "wire"))));
    assertTrue(exortEntry.matches(new MarkerItemStack(Material.PAPER, "wire")));
    assertFalse(exortEntry.matches(new MarkerItemStack(Material.PAPER, null)));
  }

  @Test
  void recipePreflightRejectsWholeCandidateWhenOneRecipeIsMalformed() {
    RecipeService service =
        new RecipeService(
            null,
            new RecordingCustomItems(),
            null,
            FeatureAccessConfig::defaults,
            testChoiceFactory());
    YamlConfiguration config = new YamlConfiguration();
    config.set("shapeless.valid.result.item", "exort:wire");
    config.set("shapeless.valid.ingredients", List.of("minecraft:redstone"));
    config.set("shapeless.invalid.result.item", "exort:wire");
    config.set("shapeless.invalid.ingredients", List.of("minecraft:not_a_material"));

    assertFalse(service.validateRecipeConfig(config));
  }

  @Test
  void recipePreflightAcceptsCompleteCandidateWithoutTouchingBukkitRegistry() {
    RecipeService service =
        new RecipeService(
            null,
            new RecordingCustomItems(),
            null,
            FeatureAccessConfig::defaults,
            testChoiceFactory());
    YamlConfiguration config = new YamlConfiguration();
    config.set("shapeless.valid.result.item", "exort:wire");
    config.set("shapeless.valid.ingredients", List.of("minecraft:redstone"));

    assertTrue(service.validateRecipeConfig(config));
    assertTrue(service.discoveryEntries().isEmpty());
  }

  @Test
  void malformedCandidateLeavesLastKnownGoodRegistryUntouched() {
    FaultInjectingRecipeRegistry registry = new FaultInjectingRecipeRegistry();
    RecipeService previous = recipeService(registry);
    assertTrue(previous.reloadReplacing(null, shapelessConfig("old"), true));
    registry.resetOperations();

    YamlConfiguration malformed = shapelessConfig("new_recipe");
    malformed.set("shapeless.invalid.ingredients", List.of("minecraft:not_a_material"));
    malformed.set("shapeless.invalid.result.item", "exort:wire");

    assertFalse(recipeService(registry).reloadReplacing(previous, malformed, true));
    assertEquals(Set.of(key("old")), registry.keys());
    assertEquals(0, registry.addCalls);
    assertEquals(0, registry.removeCalls);
    assertEquals(0, registry.getCalls);
  }

  @Test
  void rejectedCandidateRegistrationRestoresPreviousRecipesAndDiscovery() {
    assertCandidateRegistrationFailureRollsBack(false);
  }

  @Test
  void exceptionalCandidateRegistrationRestoresPreviousRecipesAndDiscovery() {
    assertCandidateRegistrationFailureRollsBack(true);
  }

  @Test
  void failureAfterRemovingConfiguredExternalRecipeRestoresIt() {
    FaultInjectingRecipeRegistry registry = new FaultInjectingRecipeRegistry();
    Recipe external = recipe(NamespacedKey.minecraft("external_recipe"));
    assertTrue(registry.add(external));
    RecipeService previous = recipeService(registry);
    assertTrue(previous.reloadReplacing(null, shapelessConfig("old"), true));

    YamlConfiguration candidate = shapelessConfig("new_recipe");
    candidate.set("disabled", List.of("minecraft:external_recipe"));
    registry.removeThenThrowOnce(NamespacedKey.minecraft("external_recipe"));

    assertFalse(recipeService(registry).reloadReplacing(previous, candidate, true));
    assertEquals(Set.of(key("old"), NamespacedKey.minecraft("external_recipe")), registry.keys());
    assertTrue(
        previous.discoveryEntries().stream().anyMatch(entry -> entry.key().equals(key("old"))));
  }

  @Test
  void successfulCandidateReplacesPreviousRecipeSetOnce() {
    FaultInjectingRecipeRegistry registry = new FaultInjectingRecipeRegistry();
    RecipeService previous = recipeService(registry);
    assertTrue(previous.reloadReplacing(null, shapelessConfig("old"), true));
    registry.resetOperations();
    RecipeService candidate = recipeService(registry);

    assertTrue(candidate.reloadReplacing(previous, shapelessConfig("first", "second"), true));
    assertEquals(Set.of(key("first"), key("second")), registry.keys());
    assertEquals(2, registry.addCalls);
    assertEquals(1, registry.removeCalls);
    assertTrue(previous.discoveryEntries().isEmpty());
    assertEquals(2, candidate.discoveryEntries().size());
  }

  private static void assertCandidateRegistrationFailureRollsBack(boolean exceptional) {
    FaultInjectingRecipeRegistry registry = new FaultInjectingRecipeRegistry();
    RecipeService previous = recipeService(registry);
    assertTrue(previous.reloadReplacing(null, shapelessConfig("old"), true));
    registry.failAddOnce(key("second"), exceptional);
    RecipeService candidate = recipeService(registry);

    assertFalse(candidate.reloadReplacing(previous, shapelessConfig("first", "second"), true));
    assertEquals(Set.of(key("old")), registry.keys());
    assertTrue(
        previous.discoveryEntries().stream().anyMatch(entry -> entry.key().equals(key("old"))));
    assertTrue(candidate.discoveryEntries().isEmpty());
  }

  private static RecipeService recipeService(RecipeRegistry registry) {
    return new RecipeService(
        null,
        new RecordingCustomItems(),
        null,
        FeatureAccessConfig::defaults,
        testChoiceFactory(),
        registry);
  }

  private static YamlConfiguration shapelessConfig(String... ids) {
    YamlConfiguration config = new YamlConfiguration();
    for (String id : ids) {
      config.set("shapeless." + id + ".result.item", "exort:wire");
      config.set("shapeless." + id + ".ingredients", List.of("minecraft:redstone"));
      config.set("shapeless." + id + ".unlock", List.of("minecraft:redstone"));
    }
    return config;
  }

  private static NamespacedKey key(String id) {
    return NamespacedKey.fromString("exort:" + id);
  }

  private static Recipe recipe(NamespacedKey key) {
    return new TestRecipe(key);
  }

  private static final class RecordingCustomItems extends CustomItems {
    private ChunkLoaderType lastType;

    private RecordingCustomItems() {
      super(
          null,
          null,
          com.zxcmc.exort.items.CustomItemModelConfig.empty(),
          com.zxcmc.exort.wireless.WirelessRuntimeConfig.defaults(),
          false);
    }

    @Override
    public ItemStack chunkLoaderItem(ChunkLoaderType type) {
      lastType = type;
      return new TestItemStack();
    }

    @Override
    public ItemStack transmitterItem() {
      return new TestItemStack();
    }

    @Override
    public ItemStack wireItem() {
      return new MarkerItemStack(Material.PAPER, "wire");
    }
  }

  private static final class TestItemStack extends ItemStack {}

  private record TestRecipe(NamespacedKey key) implements Recipe, Keyed {
    @Override
    public NamespacedKey getKey() {
      return key;
    }

    @Override
    public ItemStack getResult() {
      return new MarkerItemStack(Material.STONE, null);
    }
  }

  private static final class FaultInjectingRecipeRegistry implements RecipeRegistry {
    private final Map<NamespacedKey, Recipe> recipes = new LinkedHashMap<>();
    private NamespacedKey failedAddKey;
    private boolean exceptionalAdd;
    private NamespacedKey exceptionalRemoveKey;
    private int addCalls;
    private int removeCalls;
    private int getCalls;

    @Override
    public boolean add(Recipe recipe) {
      addCalls++;
      NamespacedKey key = ((Keyed) recipe).getKey();
      if (key.equals(failedAddKey)) {
        failedAddKey = null;
        if (exceptionalAdd) {
          throw new IllegalStateException("injected add failure for " + key);
        }
        return false;
      }
      return recipes.putIfAbsent(key, recipe) == null;
    }

    @Override
    public boolean remove(NamespacedKey key) {
      removeCalls++;
      boolean removed = recipes.remove(key) != null;
      if (key.equals(exceptionalRemoveKey)) {
        exceptionalRemoveKey = null;
        throw new IllegalStateException("injected remove failure for " + key);
      }
      return removed;
    }

    @Override
    public Recipe get(NamespacedKey key) {
      getCalls++;
      return recipes.get(key);
    }

    private void failAddOnce(NamespacedKey key, boolean exceptional) {
      failedAddKey = key;
      exceptionalAdd = exceptional;
    }

    private void removeThenThrowOnce(NamespacedKey key) {
      exceptionalRemoveKey = key;
    }

    private Set<NamespacedKey> keys() {
      return Set.copyOf(recipes.keySet());
    }

    private void resetOperations() {
      addCalls = 0;
      removeCalls = 0;
      getCalls = 0;
    }
  }

  private static RecipeService.ChoiceFactory testChoiceFactory() {
    return new RecipeService.ChoiceFactory() {
      @Override
      public RecipeChoice material(Material material) {
        return materialTriggerChoice(material);
      }

      @Override
      public RecipeChoice materials(Collection<Material> materials) {
        return materialTriggerChoice(Set.copyOf(materials));
      }

      @Override
      public RecipeChoice exact(ItemStack item) {
        return exactTriggerChoice(item);
      }
    };
  }

  private static RecipeChoice materialTriggerChoice(Material... materials) {
    return materialTriggerChoice(Set.of(materials));
  }

  private static RecipeChoice materialTriggerChoice(Set<Material> materials) {
    Set<Material> values = Set.copyOf(materials);
    return BukkitTestDoubles.proxy(
        RecipeChoice.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getItemStack" -> new MarkerItemStack(values.iterator().next(), null);
              case "clone", "validate" -> proxy;
              case "test" ->
                  args != null
                      && args.length == 1
                      && args[0] instanceof ItemStack stack
                      && values.contains(stack.getType());
              case "toString" -> "materialTriggerChoice(" + values + ")";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> BukkitTestDoubles.defaultValue(method.getReturnType());
            });
  }

  private static RecipeChoice exactTriggerChoice(ItemStack item) {
    ItemStack expected = item.clone();
    return BukkitTestDoubles.proxy(
        RecipeChoice.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getItemStack" -> expected.clone();
              case "clone", "validate" -> proxy;
              case "test" ->
                  args != null
                      && args.length == 1
                      && args[0] instanceof ItemStack stack
                      && stack.isSimilar(expected);
              case "toString" -> "exactTriggerChoice(" + expected + ")";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> BukkitTestDoubles.defaultValue(method.getReturnType());
            });
  }

  private static final class MarkerItemStack extends ItemStack {
    private final Material type;
    private final String marker;
    private int amount = 1;

    private MarkerItemStack(Material type, String marker) {
      this.type = type;
      this.marker = marker;
    }

    @Override
    public Material getType() {
      return type;
    }

    @Override
    public int getMaxStackSize() {
      return 64;
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean isSimilar(ItemStack stack) {
      if (!(stack instanceof MarkerItemStack other)) {
        return false;
      }
      return type == other.type && Objects.equals(marker, other.marker);
    }

    @Override
    public ItemStack clone() {
      MarkerItemStack copy = new MarkerItemStack(type, marker);
      copy.amount = amount;
      return copy;
    }
  }
}
