package com.zxcmc.exort.i18n;

import com.zxcmc.exort.storage.StorageTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

public final class StorageTierText {
  private static final String TEMPLATE_TOKEN = "{0}";

  private StorageTierText() {}

  public static Component storageName(Lang lang, boolean clientTranslations, StorageTier tier) {
    return applyColor(lang.itemComponent(clientTranslations, "item.storage"), tier);
  }

  public static Component storageName(Lang lang, String language, StorageTier tier) {
    return applyColor(itemText(lang.trLanguage(language, "item.storage")), tier);
  }

  public static Component tierName(Lang lang, boolean clientTranslations, StorageTier tier) {
    Component name =
        tier.translationKey()
            .map(key -> lang.itemComponent(clientTranslations, key))
            .orElseGet(() -> itemText(tier.displayName()));
    return applyColor(name, tier);
  }

  public static Component tierName(Lang lang, String language, StorageTier tier) {
    return applyColor(itemText(tierNamePlain(lang, language, tier)), tier);
  }

  public static Component tierLore(Lang lang, boolean clientTranslations, StorageTier tier) {
    Component name = tierName(lang, clientTranslations, tier);
    if (!clientTranslations) {
      return replaceToken(lang.tr("lore.storage.tier", TEMPLATE_TOKEN), name);
    }
    return Component.translatable(
            lang.clientKey("lore.storage.tier"), lang.tr("lore.storage.tier", name), name)
        .color(NamedTextColor.WHITE)
        .decoration(TextDecoration.ITALIC, false);
  }

  public static Component tierValueLore(Lang lang, boolean clientTranslations, StorageTier tier) {
    return tierName(lang, clientTranslations, tier);
  }

  public static Component tierLore(Lang lang, String language, StorageTier tier) {
    String template = lang.trLanguage(language, "lore.storage.tier", TEMPLATE_TOKEN);
    return replaceToken(template, tierName(lang, language, tier));
  }

  public static Component tierValueLore(Lang lang, String language, StorageTier tier) {
    return tierName(lang, language, tier);
  }

  public static String tierNamePlain(Lang lang, Player player, StorageTier tier) {
    return tierNamePlain(lang, lang.pluginTextLanguage(player), tier);
  }

  public static String tierNamePlain(Lang lang, String language, StorageTier tier) {
    return tier.translationKey()
        .map(key -> lang.trLanguage(language, key))
        .orElseGet(tier::displayName);
  }

  public static String storageLabelWithTier(Lang lang, Player player, StorageTier tier) {
    return storageLabelWithTier(lang, lang.pluginTextLanguage(player), tier);
  }

  public static String storageLabelWithTier(Lang lang, String language, StorageTier tier) {
    return lang.trLanguage(language, "item.storage")
        + " ("
        + tierNamePlain(lang, language, tier)
        + ")";
  }

  private static Component applyColor(Component component, StorageTier tier) {
    TextColor color = tier.color();
    Component result = color == null ? component : component.color(color);
    return result.decoration(TextDecoration.ITALIC, false);
  }

  private static Component itemText(String value) {
    return Component.text(value == null ? "" : value).decoration(TextDecoration.ITALIC, false);
  }

  private static Component loreLabelText(String value) {
    return itemText(value).color(NamedTextColor.WHITE);
  }

  private static Component replaceToken(String template, Component replacement) {
    if (template == null || template.isEmpty()) {
      return Component.empty().decoration(TextDecoration.ITALIC, false);
    }
    Component result = Component.empty();
    int pos = 0;
    int next;
    while ((next = template.indexOf(TEMPLATE_TOKEN, pos)) >= 0) {
      if (next > pos) {
        result = result.append(loreLabelText(template.substring(pos, next)));
      }
      result = result.append(replacement);
      pos = next + TEMPLATE_TOKEN.length();
    }
    if (pos < template.length()) {
      result = result.append(loreLabelText(template.substring(pos)));
    }
    return result.decoration(TextDecoration.ITALIC, false);
  }
}
