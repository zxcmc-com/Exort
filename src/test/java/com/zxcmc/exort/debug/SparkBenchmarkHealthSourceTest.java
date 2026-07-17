package com.zxcmc.exort.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Map;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.gc.GarbageCollector;
import me.lucko.spark.api.statistic.StatisticWindow.CpuUsage;
import me.lucko.spark.api.statistic.StatisticWindow.MillisPerTick;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.DoubleStatistic;
import me.lucko.spark.api.statistic.types.GenericStatistic;
import org.junit.jupiter.api.Test;

class SparkBenchmarkHealthSourceTest {
  @Test
  void missingServiceProducesUnavailableSample() {
    BenchmarkHealthSource.Sample sample = new SparkBenchmarkHealthSource(() -> null).capture();

    assertFalse(sample.available());
  }

  @Test
  void capturesOneMinuteMsptCpuAndGcTotals() {
    DoubleAverageInfo mspt = averageInfo(12.5, 19.0, 30.0);
    GarbageCollector collector = garbageCollector(7L, 42L);
    Spark spark = spark(mspt, 0.375, Map.of("G1 Young", collector));

    BenchmarkHealthSource.Sample sample = new SparkBenchmarkHealthSource(() -> spark).capture();

    assertTrue(sample.available());
    assertSame(spark, sample.providerIdentity());
    assertEquals(12.5, sample.msptMean().orElseThrow());
    assertEquals(19.0, sample.msptP95().orElseThrow());
    assertEquals(30.0, sample.msptMax().orElseThrow());
    assertEquals(0.375, sample.processCpu().orElseThrow());
    assertEquals(7L, sample.garbageCollectors().get("G1 Young").collections());
    assertEquals(42L, sample.garbageCollectors().get("G1 Young").timeMillis());
  }

  @Test
  void nullOptionalStatisticsStillIdentifyAvailableProvider() {
    Spark spark = spark(null, null, null);

    BenchmarkHealthSource.Sample sample = new SparkBenchmarkHealthSource(() -> spark).capture();

    assertTrue(sample.available());
    assertTrue(sample.msptMean().isEmpty());
    assertTrue(sample.processCpu().isEmpty());
    assertTrue(sample.garbageCollectors().isEmpty());
  }

  @SuppressWarnings("unchecked")
  private static Spark spark(
      DoubleAverageInfo msptInfo, Double cpuValue, Map<String, GarbageCollector> collectors) {
    GenericStatistic<DoubleAverageInfo, MillisPerTick> mspt =
        msptInfo == null
            ? null
            : (GenericStatistic<DoubleAverageInfo, MillisPerTick>)
                Proxy.newProxyInstance(
                    SparkBenchmarkHealthSourceTest.class.getClassLoader(),
                    new Class<?>[] {GenericStatistic.class},
                    (proxy, method, args) ->
                        switch (method.getName()) {
                          case "poll" ->
                              args == null || args.length == 0
                                  ? new DoubleAverageInfo[0]
                                  : msptInfo;
                          case "name" -> "MSPT";
                          case "getWindows" -> MillisPerTick.values();
                          default -> throw new UnsupportedOperationException(method.getName());
                        });
    DoubleStatistic<CpuUsage> cpu =
        cpuValue == null
            ? null
            : (DoubleStatistic<CpuUsage>)
                Proxy.newProxyInstance(
                    SparkBenchmarkHealthSourceTest.class.getClassLoader(),
                    new Class<?>[] {DoubleStatistic.class},
                    (proxy, method, args) ->
                        switch (method.getName()) {
                          case "poll" ->
                              args == null || args.length == 0 ? new double[0] : cpuValue;
                          case "name" -> "CPU";
                          case "getWindows" -> CpuUsage.values();
                          default -> throw new UnsupportedOperationException(method.getName());
                        });
    return (Spark)
        Proxy.newProxyInstance(
            SparkBenchmarkHealthSourceTest.class.getClassLoader(),
            new Class<?>[] {Spark.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "mspt" -> mspt;
                  case "cpuProcess" -> cpu;
                  case "gc" -> collectors;
                  case "cpuSystem", "tps", "placeholders" -> null;
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  private static DoubleAverageInfo averageInfo(double mean, double p95, double max) {
    return new DoubleAverageInfo() {
      @Override
      public double mean() {
        return mean;
      }

      @Override
      public double max() {
        return max;
      }

      @Override
      public double min() {
        return mean;
      }

      @Override
      public double percentile(double percentile) {
        return percentile == 0.95 ? p95 : mean;
      }
    };
  }

  private static GarbageCollector garbageCollector(long collections, long timeMillis) {
    return new GarbageCollector() {
      @Override
      public String name() {
        return "collector";
      }

      @Override
      public long totalCollections() {
        return collections;
      }

      @Override
      public long totalTime() {
        return timeMillis;
      }

      @Override
      public double avgTime() {
        return 0.0;
      }

      @Override
      public long avgFrequency() {
        return 0L;
      }
    };
  }
}
