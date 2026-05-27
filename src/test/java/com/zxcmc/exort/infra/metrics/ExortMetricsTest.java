package com.zxcmc.exort.infra.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
