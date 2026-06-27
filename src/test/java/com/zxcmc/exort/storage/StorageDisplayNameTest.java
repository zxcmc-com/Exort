package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.i18n.Lang;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StorageDisplayNameTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void normalizesStorageDisplayNames() {
    assertEquals("Main Vault", StorageDisplayName.normalize("  Main Vault  "));
    assertEquals("MainVault", StorageDisplayName.normalize("Main\u0000\u001fVault"));
    assertNull(StorageDisplayName.normalize(" \n\t "));
    assertEquals("A".repeat(64), StorageDisplayName.normalize("A".repeat(80)));
  }

  @Test
  void labelUsesCustomNameWhenPresentAndTierFallbackWhenBlank() {
    loadRareTier();
    Lang lang = new Lang(null);
    lang.load("en_us");
    StorageTier tier = StorageTier.fromString("rare").orElseThrow();

    String custom = StorageDisplayName.label(lang, "ru_ru", tier, " Main Vault ");
    String fallback = StorageDisplayName.label(lang, "ru_ru", tier, " ");

    assertTrue(custom.contains("Main Vault"));
    assertFalse(fallback.isBlank());
    assertFalse(fallback.contains("Main Vault"));
  }

  @Test
  void anvilInputStripsDisplayedStoragePrefixBeforePersistence() {
    Lang lang = new Lang(null);
    lang.load("ru_ru");

    assertEquals(
        "Main Vault", StorageDisplayName.normalizeAnvilInput(lang, "Хранилище: Main Vault"));
    assertEquals("Main Vault", StorageDisplayName.normalizeAnvilInput(lang, "Storage: Main Vault"));
    assertNull(StorageDisplayName.normalizeAnvilInput(lang, "Хранилище:   "));
  }

  @Test
  void customNameKeepsPrefixNonItalicAndPlayerNameItalic() {
    Component component = StorageDisplayName.customNameComponent("Main Vault");

    assertEquals(TextDecoration.State.FALSE, component.decoration(TextDecoration.ITALIC));
    assertEquals(
        TextDecoration.State.TRUE, component.children().get(1).decoration(TextDecoration.ITALIC));
    assertEquals(NamedTextColor.WHITE, component.children().get(1).color());
    assertEquals(
        "Main Vault", PLAIN.serialize(StorageDisplayName.anvilInputComponent("Main Vault")));
  }

  @Test
  void customPlayerNameDoesNotInheritTierColor() {
    loadRareTier();
    Lang lang = new Lang(null);
    lang.load("ru_ru");
    StorageTier tier = StorageTier.fromString("rare").orElseThrow();

    Component component = StorageDisplayName.customNameComponent(lang, "ru_ru", tier, "Main Vault");

    assertEquals(NamedTextColor.RED, component.color());
    assertEquals(NamedTextColor.RED, component.children().get(0).color());
    assertEquals(NamedTextColor.WHITE, component.children().get(1).color());
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
