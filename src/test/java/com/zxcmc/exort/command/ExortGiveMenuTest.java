package com.zxcmc.exort.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.storage.StorageTier;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class ExortGiveMenuTest {
  private static final Logger LOGGER = Logger.getLogger(ExortGiveMenuTest.class.getName());

  @Test
  void defaultCatalogFitsSingleInventory() {
    loadDefaultLikeTiers();

    assertTrue(ExortGiveMenu.catalogIds().size() <= ExortGiveMenu.SIZE);
  }

  @Test
  void catalogSizeValidationRejectsOverflow() {
    assertThrows(IllegalStateException.class, () -> ExortGiveMenu.validateCatalogSize(55));
  }

  @Test
  void cursorTrashUsesVanillaLikeClickAmounts() {
    assertEquals(12, ExortGiveMenu.cursorDestroyAmount(12, ClickType.LEFT));
    assertEquals(1, ExortGiveMenu.cursorDestroyAmount(12, ClickType.RIGHT));
    assertEquals(0, ExortGiveMenu.cursorDestroyAmount(12, ClickType.SHIFT_LEFT));
    assertEquals(0, ExortGiveMenu.cursorDestroyAmount(0, ClickType.LEFT));
  }

  @Test
  void trashRejectsVanillaItemsAndInitializedStorages() {
    assertTrue(ExortGiveMenu.canDestroyCustomItem(true, false));
    assertFalse(ExortGiveMenu.canDestroyCustomItem(false, false));
    assertFalse(ExortGiveMenu.canDestroyCustomItem(true, true));
  }

  private static void loadDefaultLikeTiers() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("common.maxItems", "1p");
    config.set("common.material", Material.GRAY_SHULKER_BOX.name());
    config.set("common.name", "{tier.common}");
    config.set("rare.maxItems", "5p");
    config.set("rare.material", Material.LIGHT_BLUE_SHULKER_BOX.name());
    config.set("rare.name", "{tier.rare}");
    config.set("mythical.maxItems", "20p");
    config.set("mythical.material", Material.SHULKER_BOX.name());
    config.set("mythical.name", "{tier.mythical}");
    config.set("legendary.maxItems", "100p");
    config.set("legendary.material", Material.MAGENTA_SHULKER_BOX.name());
    config.set("legendary.name", "{tier.legendary}");
    config.set("immortal.maxItems", "500p");
    config.set("immortal.material", Material.YELLOW_SHULKER_BOX.name());
    config.set("immortal.name", "{tier.immortal}");

    StorageTier.loadFromConfig(config, LOGGER);
  }
}
