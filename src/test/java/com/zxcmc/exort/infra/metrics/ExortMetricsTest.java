package com.zxcmc.exort.infra.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.integration.protection.ProtectionRuntimeConfig;
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
  void languageChartValueDoesNotForwardArbitraryConfigText() {
    assertEquals("custom_or_invalid", ExortMetrics.languageChartValue(""));
    assertEquals("custom_or_invalid", ExortMetrics.languageChartValue("not a locale"));
    assertEquals("custom_or_invalid", ExortMetrics.languageChartValue("token=secret"));
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
