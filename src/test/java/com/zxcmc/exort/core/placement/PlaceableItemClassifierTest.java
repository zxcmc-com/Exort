package com.zxcmc.exort.core.placement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class PlaceableItemClassifierTest {
  @Test
  void detectsNormalBlocks() {
    assertTrue(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.STONE)));
  }

  @Test
  void detectsCommonUseOnBlockPlacementItems() {
    assertTrue(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.WATER_BUCKET)));
    assertTrue(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.ITEM_FRAME)));
    assertTrue(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.OAK_BOAT)));
    assertTrue(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.MINECART)));
    assertTrue(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.ZOMBIE_SPAWN_EGG)));
    assertTrue(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.ARMOR_STAND)));
  }

  @Test
  void ignoresNonPlacementItems() {
    assertFalse(PlaceableItemClassifier.isPotentialPlacementItem(null));
    assertFalse(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.AIR)));
    assertFalse(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.PAPER)));
    assertFalse(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.SHIELD)));
    assertFalse(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.APPLE)));
    assertFalse(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.DIAMOND_SWORD)));
    assertFalse(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.BUCKET)));
    assertFalse(PlaceableItemClassifier.isPotentialPlacementItem(item(Material.MILK_BUCKET)));
  }

  private static ItemStack item(Material type) {
    return new TestItemStack(type);
  }

  private static final class TestItemStack extends ItemStack {
    private final Material type;

    TestItemStack(Material type) {
      this.type = type;
    }

    @Override
    public Material getType() {
      return type;
    }
  }
}
