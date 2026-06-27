package com.zxcmc.exort.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.storage.StorageTier;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BusSessionManagerTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void humanizeMaterialFormatsRawMaterialNamesForFallbacks() {
    assertEquals("Blast Furnace", BusSessionManager.humanizeMaterial(Material.BLAST_FURNACE));
    assertEquals("Chest", BusSessionManager.humanizeMaterial(Material.CHEST));
  }

  @Test
  void busStorageInfoUsesRawCustomNameForTranslatedStorageLabel() {
    loadRareTier();
    Lang lang = new Lang(null);
    lang.load("en_us");
    StorageTier tier = StorageTier.fromString("rare").orElseThrow();

    Component value = BusSessionManager.storageValue(lang, null, tier, "storage-id", " example ");

    assertEquals("example", plain(value));
    assertEquals(NamedTextColor.RED, value.color());
  }

  @Test
  void busStorageInfoFallsBackToTierNameWithoutCustomName() {
    loadRareTier();
    Lang lang = new Lang(null);
    lang.load("en_us");
    StorageTier tier = StorageTier.fromString("rare").orElseThrow();

    Component value = BusSessionManager.storageValue(lang, null, tier, "storage-id", null);

    assertEquals("Rare", plain(value));
    assertEquals(NamedTextColor.RED, value.color());
  }

  private static String plain(Component component) {
    return PLAIN.serialize(component);
  }

  private static void loadRareTier() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("rare.maxItems", "5p");
    config.set("rare.material", "CHEST");
    config.set("rare.name", "{tier.rare}");
    config.set("rare.color", "red");
    StorageTier.loadFromConfig(config, Logger.getLogger("test"));
  }
}
