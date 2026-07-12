package com.zxcmc.exort.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.nio.file.Path;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class WirelessBoosterTextTest {
  @Test
  void formatsConfiguredBonusWithoutRedundantFractionDigits() {
    assertEquals("+50", WirelessBoosterText.formatBonusPercent(1.5D));
    assertEquals("+100", WirelessBoosterText.formatBonusPercent(2.0D));
    assertEquals("+500", WirelessBoosterText.formatBonusPercent(6.0D));
    assertEquals("-100", WirelessBoosterText.formatBonusPercent(0.0D));
    assertEquals("+0", WirelessBoosterText.formatBonusPercent(1.0D));
    assertEquals("+23.46", WirelessBoosterText.formatBonusPercent(1.23456D));
  }

  @Test
  void describesGlobalImmortalCoverageInsteadOfAPercentage() {
    Lang lang = new Lang(null, null, Path.of("src/main/resources"));
    lang.load("en_us");

    assertEquals(
        java.util.List.of("Tier: Immortal", "Works throughout the current world"),
        WirelessBoosterText.lore(
                lang, "en_us", WirelessRuntimeConfig.defaults(), WirelessBoosterTier.IMMORTAL)
            .stream()
            .map(PlainTextComponentSerializer.plainText()::serialize)
            .toList());
  }
}
