package com.zxcmc.exort.storage;

import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.i18n.StorageTierText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

public final class StorageDisplayName {
  private static final String STORAGE_KEY = "item.storage";
  private static final String DEFAULT_STORAGE_LABEL = "Storage";

  private StorageDisplayName() {}

  public static Component customNameComponent(String raw) {
    String normalized = StorageNameNormalizer.normalize(raw);
    if (normalized == null) {
      return null;
    }
    return prefixedCustomName(Component.text(DEFAULT_STORAGE_LABEL), normalized);
  }

  public static Component customNameComponent(Lang lang, boolean clientTranslations, String raw) {
    String normalized = StorageNameNormalizer.normalize(raw);
    if (normalized == null) {
      return null;
    }
    Component prefix =
        lang == null
            ? Component.text(DEFAULT_STORAGE_LABEL)
            : lang.clientComponent(clientTranslations, STORAGE_KEY);
    return prefixedCustomName(prefix, normalized);
  }

  public static Component customNameComponent(
      Lang lang, boolean clientTranslations, StorageTier tier, String raw) {
    String normalized = StorageNameNormalizer.normalize(raw);
    if (normalized == null) {
      return null;
    }
    Component prefix =
        lang == null
            ? Component.text(DEFAULT_STORAGE_LABEL)
            : tier == null
                ? lang.clientComponent(clientTranslations, STORAGE_KEY)
                : StorageTierText.storageName(lang, clientTranslations, tier);
    return prefixedCustomName(prefix, normalized);
  }

  public static Component customNameComponent(Lang lang, String language, String raw) {
    String normalized = StorageNameNormalizer.normalize(raw);
    if (normalized == null) {
      return null;
    }
    return prefixedCustomName(Component.text(storageLabel(lang, language)), normalized);
  }

  public static Component customNameComponent(
      Lang lang, String language, StorageTier tier, String raw) {
    String normalized = StorageNameNormalizer.normalize(raw);
    if (normalized == null) {
      return null;
    }
    Component prefix =
        lang == null
            ? Component.text(DEFAULT_STORAGE_LABEL)
            : tier == null
                ? Component.text(storageLabel(lang, language))
                : StorageTierText.storageName(lang, language, tier);
    return prefixedCustomName(prefix, normalized);
  }

  public static Component anvilInputComponent(String raw) {
    String normalized = StorageNameNormalizer.normalize(raw);
    if (normalized == null) {
      return null;
    }
    return Component.text(normalized).decoration(TextDecoration.ITALIC, true);
  }

  public static Component displayComponent(Component fallback, StorageTier tier, String raw) {
    String normalized = StorageNameNormalizer.normalize(raw);
    Component component =
        normalized == null ? fallback : prefixedCustomName(storagePrefix(fallback), normalized);
    if (component == null) {
      component = Component.empty();
    }
    TextColor color = tier == null ? null : tier.color();
    if (color != null) {
      component = component.color(color);
    }
    return normalized == null ? component.decoration(TextDecoration.ITALIC, false) : component;
  }

  public static String label(Lang lang, Player player, StorageTier tier, String raw) {
    return label(lang, player == null ? null : lang.pluginTextLanguage(player), tier, raw);
  }

  public static String label(Lang lang, String language, StorageTier tier, String raw) {
    String normalized = StorageNameNormalizer.normalize(raw);
    if (normalized != null) {
      return storageLabel(lang, language) + ": " + normalized;
    }
    if (lang == null || tier == null) {
      return "";
    }
    return StorageTierText.storageLabelWithTier(lang, language, tier);
  }

  private static String storageLabel(Lang lang, String language) {
    if (lang == null) {
      return DEFAULT_STORAGE_LABEL;
    }
    return lang.trLanguage(language, STORAGE_KEY);
  }

  private static Component storagePrefix(Component fallback) {
    return fallback == null ? Component.text(DEFAULT_STORAGE_LABEL) : fallback;
  }

  private static Component prefixedCustomName(Component prefix, String normalized) {
    Component styledPrefix =
        (prefix == null ? Component.text(DEFAULT_STORAGE_LABEL) : prefix)
            .decoration(TextDecoration.ITALIC, false);
    TextColor color = styledPrefix.color();
    Component separator = Component.text(": ").decoration(TextDecoration.ITALIC, false);
    Component name =
        Component.text(normalized)
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, true);
    if (color != null) {
      separator = separator.color(color);
    }
    return styledPrefix.append(separator).append(name);
  }
}
