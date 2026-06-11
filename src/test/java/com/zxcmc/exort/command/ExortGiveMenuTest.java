package com.zxcmc.exort.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.storage.StorageTier;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class ExortGiveMenuTest {
  private static final Logger LOGGER = Logger.getLogger(ExortGiveMenuTest.class.getName());
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void catalogListsStorageTiersBeforeFixedItems() {
    loadDefaultLikeTiers();

    assertEquals(
        List.of(
            "storage:gold",
            "storage:diamond",
            "storage:netherite",
            "storage_core",
            "terminal",
            "crafting_terminal",
            "monitor",
            "import_bus",
            "export_bus",
            "wire",
            "wireless_terminal"),
        ExortGiveMenu.catalogIds());
  }

  @Test
  void defaultCatalogFitsSingleInventory() {
    loadDefaultLikeTiers();

    assertTrue(ExortGiveMenu.catalogIds().size() <= ExortGiveMenu.SIZE);
  }

  @Test
  void titleUsesLocalizedTextWithLegacyFallback() {
    assertEquals("Предметы Exort", PLAIN.serialize(ExortGiveMenu.title("Предметы Exort")));
    assertEquals(ExortGiveMenu.TITLE, PLAIN.serialize(ExortGiveMenu.title(" ")));
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
    config.set("gold.maxItems", 5760);
    config.set("gold.material", Material.GOLD_BLOCK.name());
    config.set("gold.displayName", "Gold Storage");
    config.set("diamond.maxItems", "10p");
    config.set("diamond.material", Material.DIAMOND_BLOCK.name());
    config.set("diamond.displayName", "Diamond Storage");
    config.set("netherite.maxItems", "100p");
    config.set("netherite.material", Material.NETHERITE_BLOCK.name());
    config.set("netherite.displayName", "Netherite Storage");

    StorageTier.loadFromConfig(config, LOGGER);
  }
}
