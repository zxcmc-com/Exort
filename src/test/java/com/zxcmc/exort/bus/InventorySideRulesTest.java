package com.zxcmc.exort.bus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class InventorySideRulesTest {
  @Test
  void brewingBottleSlotsAcceptOnlyPotionBottles() {
    assertTrue(canInsert(Material.POTION, 0));
    assertTrue(canInsert(Material.SPLASH_POTION, 1));
    assertTrue(canInsert(Material.LINGERING_POTION, 2));

    assertFalse(canInsert(Material.GLASS_BOTTLE, 0));
    assertFalse(canInsert(Material.DIAMOND, 1));
  }

  @Test
  void brewingFuelSlotAcceptsOnlyBrewingFuel() {
    assertTrue(canInsert(Material.BLAZE_POWDER, 4));

    assertFalse(canInsert(Material.BLAZE_ROD, 4));
    assertFalse(canInsert(Material.NETHER_WART, 4));
  }

  @Test
  void brewingIngredientSlotUsesVanillaLikeAllowlist() {
    assertTrue(canInsert(Material.NETHER_WART, 3));
    assertTrue(canInsert(Material.BREEZE_ROD, 3));
    assertTrue(canInsert(Material.STONE, 3));

    assertFalse(canInsert(Material.BLAZE_POWDER, 3));
    assertFalse(canInsert(Material.DIAMOND, 3));
  }

  private boolean canInsert(Material material, int slot) {
    return InventorySideRules.canInsertBrewing(material, slot);
  }
}
