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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
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

  private static final class RecordingCustomItems extends CustomItems {
    private ChunkLoaderType lastType;

    private RecordingCustomItems() {
      super(null, null, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", false);
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

    private MarkerItemStack(Material type, String marker) {
      this.type = type;
      this.marker = marker;
    }

    @Override
    public Material getType() {
      return type;
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
      return new MarkerItemStack(type, marker);
    }
  }
}
