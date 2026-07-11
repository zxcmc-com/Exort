package com.zxcmc.exort.infra.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.i18n.LocalizationFiles;
import com.zxcmc.exort.integration.protection.ProtectionRuntimeConfig;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExortMetricsTest {
  @Test
  void languageChartValueKeepsLocaleCodesCategorical() {
    assertEquals("en_us", ExortMetrics.languageChartValue(null));
    assertEquals("ru_ru", ExortMetrics.languageChartValue("RU-RU"));
    assertEquals("nds_de", ExortMetrics.languageChartValue("nds_de"));
    assertEquals("ksh", ExortMetrics.languageChartValue("ksh"));
  }

  @Test
  void languageChartValueIsLimitedToBundledLocalesAndOther() throws Exception {
    Set<String> expectedBundled;
    try (InputStream input =
        ExortMetricsTest.class.getClassLoader().getResourceAsStream(LocalizationFiles.LANG_INDEX)) {
      assertTrue(input != null, "bundled language index is missing");
      LinkedHashSet<String> expected =
          new LinkedHashSet<>(LocalizationFiles.readLanguageIndex(input));
      expected.add(LocalizationFiles.DEFAULT_LANGUAGE);
      expectedBundled = Set.copyOf(expected);
    }
    Set<String> bundled = ExortMetrics.bundledLanguageChartValues();

    assertEquals(expectedBundled, bundled);
    assertTrue(bundled.contains("en_us"));
    assertTrue(bundled.contains("ru_ru"));
    for (String locale : bundled) {
      assertEquals(locale, ExortMetrics.languageChartValue(locale));
    }

    assertEquals("other", ExortMetrics.languageChartValue(""));
    assertEquals("other", ExortMetrics.languageChartValue("zz_zz"));
    assertEquals("other", ExortMetrics.languageChartValue("not a locale"));
    assertEquals("other", ExortMetrics.languageChartValue("token=secret"));
  }

  @Test
  void protectionStateChartValueUsesBoundedCategories() {
    ProtectionRuntimeConfig enabled =
        new ProtectionRuntimeConfig(
            true, false, new ProtectionRuntimeConfig.Adapters(true, true, true, true, true));

    assertEquals("missing", ExortMetrics.protectionStateChartValue(enabled, pluginName -> false));
    assertEquals(
        "worldguard", ExortMetrics.protectionStateChartValue(enabled, "WorldGuard"::equals));
    assertEquals(
        "multiple",
        ExortMetrics.protectionStateChartValue(
            enabled, pluginName -> pluginName.equals("WorldGuard") || pluginName.equals("Towny")));
    assertEquals(
        "disabled",
        ExortMetrics.protectionStateChartValue(
            new ProtectionRuntimeConfig(
                false, false, new ProtectionRuntimeConfig.Adapters(true, true, true, true, true)),
            pluginName -> true));
  }
}
