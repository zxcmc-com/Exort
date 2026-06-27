package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StorageGuiControlsTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void infoLoreIncludesTierCustomNameAndIdInStableOrder() {
    loadRareTier();
    Lang lang = new Lang(null);
    lang.load("en_us");
    StorageTier tier = StorageTier.fromString("rare").orElseThrow();
    StorageCache cache = new StorageCache("storage-id", null, Logger.getLogger("test"), () -> null);
    cache.setDisplayName("example");

    List<Component> lore =
        StorageGuiControls.infoLore(lang, null, cache, tier, true, false, false, null, 0, false);

    List<String> lines = plain(lore);
    assertEquals(3, lines.size());
    assertContainsInOrder(lines, "Rare", "example", "storage-id");
  }

  private static void assertContainsInOrder(List<String> lines, String... needles) {
    int lineIndex = 0;
    for (String needle : needles) {
      while (lineIndex < lines.size() && !lines.get(lineIndex).contains(needle)) {
        lineIndex++;
      }
      assertTrue(lineIndex < lines.size(), "missing " + needle + " in " + lines);
      lineIndex++;
    }
  }

  private static List<String> plain(List<Component> lore) {
    return lore.stream().map(PLAIN::serialize).toList();
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
