package com.zxcmc.exort.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zxcmc.exort.storage.StorageTier;
import java.nio.file.Path;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StorageTierTextTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void tierTextResolvesTranslationsAndKeepsTierColorSeparatedFromLabels() {
    loadRareTier();
    Lang lang = new Lang(null, null, Path.of("src/main/resources"));
    lang.load("en_us");
    StorageTier tier = StorageTier.fromString("rare").orElseThrow();

    Component englishName = StorageTierText.storageName(lang, "en_us", tier);

    assertFalse(plain(englishName).isBlank());
    assertFalse(plain(englishName).contains("{"));
    assertEquals(NamedTextColor.RED, firstColor(englishName));

    Component englishLore = StorageTierText.tierLore(lang, "en_us", tier);
    Component russianLore = StorageTierText.tierLore(lang, "ru_ru", tier);
    Component clientLore = StorageTierText.tierLore(lang, true, tier);
    Component storageItemTier = StorageTierText.tierValueLore(lang, "ru_ru", tier);
    assertFalse(plain(englishLore).contains("{"));
    assertFalse(plain(russianLore).contains("{"));
    assertFalse(plain(storageItemTier).isBlank());
    assertEquals(NamedTextColor.WHITE, firstColor(englishLore));
    assertEquals(NamedTextColor.RED, lastColor(englishLore));
    assertEquals(NamedTextColor.WHITE, firstColor(russianLore));
    assertEquals(NamedTextColor.RED, lastColor(russianLore));
    assertEquals(NamedTextColor.RED, firstColor(storageItemTier));
    assertEquals(NamedTextColor.WHITE, clientLore.color());
  }

  private static void loadRareTier() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("rare.maxItems", "5p");
    config.set("rare.material", "CHEST");
    config.set("rare.name", "{tier.rare}");
    config.set("rare.color", "<red>");
    StorageTier.loadFromConfig(config, Logger.getLogger("test"));
  }

  private static String plain(Component component) {
    return PLAIN.serialize(component);
  }

  private static TextColor firstColor(Component component) {
    TextColor color = component.color();
    if (color != null) {
      return color;
    }
    for (Component child : component.children()) {
      TextColor childColor = firstColor(child);
      if (childColor != null) {
        return childColor;
      }
    }
    return null;
  }

  private static TextColor lastColor(Component component) {
    for (int i = component.children().size() - 1; i >= 0; i--) {
      TextColor childColor = lastColor(component.children().get(i));
      if (childColor != null) {
        return childColor;
      }
    }
    return component.color();
  }
}
