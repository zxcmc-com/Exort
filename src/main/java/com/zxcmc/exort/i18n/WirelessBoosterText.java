package com.zxcmc.exort.i18n;

import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class WirelessBoosterText {
  private static final String TEMPLATE_TOKEN = "{0}";

  private WirelessBoosterText() {}

  public static List<Component> lore(
      Lang lang,
      boolean clientTranslations,
      WirelessRuntimeConfig config,
      WirelessBoosterTier tier) {
    WirelessRuntimeConfig effectiveConfig =
        config == null ? WirelessRuntimeConfig.defaults() : config;
    Component tierName =
        lang.itemComponent(clientTranslations, tier.translationKey())
            .color(tier.color())
            .decoration(TextDecoration.ITALIC, false);
    Component tierLine;
    if (clientTranslations) {
      tierLine =
          Component.translatable(
                  lang.clientKey("lore.wireless_booster.tier"),
                  lang.tr("lore.wireless_booster.tier", tierName),
                  tierName)
              .color(NamedTextColor.WHITE)
              .decoration(TextDecoration.ITALIC, false);
    } else {
      tierLine = replaceToken(lang.tr("lore.wireless_booster.tier", TEMPLATE_TOKEN), tierName);
    }
    return List.of(tierLine, bonusLine(lang, clientTranslations, effectiveConfig, tier));
  }

  public static List<Component> lore(
      Lang lang, String language, WirelessRuntimeConfig config, WirelessBoosterTier tier) {
    WirelessRuntimeConfig effectiveConfig =
        config == null ? WirelessRuntimeConfig.defaults() : config;
    Component tierName =
        Component.text(lang.trLanguage(language, tier.translationKey()))
            .color(tier.color())
            .decoration(TextDecoration.ITALIC, false);
    Component tierLine =
        replaceToken(
            lang.trLanguage(language, "lore.wireless_booster.tier", TEMPLATE_TOKEN), tierName);
    String bonus =
        effectiveConfig.isGlobal(tier)
            ? lang.trLanguage(language, "lore.wireless_booster.global")
            : lang.trLanguage(
                language,
                "lore.wireless_booster.range_bonus",
                formatBonusPercent(effectiveConfig.boosterRangeMultiplier(tier)));
    return List.of(tierLine, loreText(bonus));
  }

  static String formatBonusPercent(double multiplier) {
    BigDecimal percent =
        BigDecimal.valueOf(multiplier)
            .subtract(BigDecimal.ONE)
            .multiply(BigDecimal.valueOf(100L))
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros();
    if (percent.signum() == 0) {
      return "+0";
    }
    String value = percent.toPlainString();
    return percent.signum() > 0 ? "+" + value : value;
  }

  private static Component bonusLine(
      Lang lang,
      boolean clientTranslations,
      WirelessRuntimeConfig config,
      WirelessBoosterTier tier) {
    Component line =
        config.isGlobal(tier)
            ? lang.itemComponent(clientTranslations, "lore.wireless_booster.global")
            : lang.itemComponent(
                clientTranslations,
                "lore.wireless_booster.range_bonus",
                formatBonusPercent(config.boosterRangeMultiplier(tier)));
    return line.color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
  }

  private static Component replaceToken(String template, Component replacement) {
    if (template == null || template.isEmpty()) {
      return Component.empty().decoration(TextDecoration.ITALIC, false);
    }
    Component result = Component.empty();
    int position = 0;
    int next;
    while ((next = template.indexOf(TEMPLATE_TOKEN, position)) >= 0) {
      if (next > position) {
        result = result.append(loreText(template.substring(position, next)));
      }
      result = result.append(replacement);
      position = next + TEMPLATE_TOKEN.length();
    }
    if (position < template.length()) {
      result = result.append(loreText(template.substring(position)));
    }
    return result.decoration(TextDecoration.ITALIC, false);
  }

  private static Component loreText(String value) {
    return Component.text(value == null ? "" : value)
        .color(NamedTextColor.WHITE)
        .decoration(TextDecoration.ITALIC, false);
  }
}
