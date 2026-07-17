package com.zxcmc.exort.debug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import me.lucko.spark.api.Spark;
import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.gc.GarbageCollector;
import me.lucko.spark.api.statistic.StatisticWindow.CpuUsage;
import me.lucko.spark.api.statistic.StatisticWindow.MillisPerTick;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.DoubleStatistic;
import me.lucko.spark.api.statistic.types.GenericStatistic;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

final class SparkBenchmarkHealthSource implements BenchmarkHealthSource {
  private final Supplier<Spark> serviceLookup;

  SparkBenchmarkHealthSource() {
    this(SparkBenchmarkHealthSource::availableService);
  }

  SparkBenchmarkHealthSource(Supplier<Spark> serviceLookup) {
    this.serviceLookup = Objects.requireNonNull(serviceLookup, "serviceLookup");
  }

  @Override
  public Sample capture() {
    Spark spark = serviceLookup.get();
    if (spark == null) {
      return Sample.unavailable();
    }

    OptionalDouble msptMean = OptionalDouble.empty();
    OptionalDouble msptP95 = OptionalDouble.empty();
    OptionalDouble msptMax = OptionalDouble.empty();
    GenericStatistic<DoubleAverageInfo, MillisPerTick> mspt = spark.mspt();
    if (mspt != null) {
      DoubleAverageInfo minute = mspt.poll(MillisPerTick.MINUTES_1);
      if (minute != null) {
        msptMean = OptionalDouble.of(minute.mean());
        msptP95 = OptionalDouble.of(minute.percentile95th());
        msptMax = OptionalDouble.of(minute.max());
      }
    }

    OptionalDouble processCpu = OptionalDouble.empty();
    DoubleStatistic<CpuUsage> cpu = spark.cpuProcess();
    if (cpu != null) {
      processCpu = OptionalDouble.of(cpu.poll(CpuUsage.MINUTES_1));
    }

    Map<String, GcTotals> garbageCollectors = new LinkedHashMap<>();
    Map<String, GarbageCollector> sparkCollectors = spark.gc();
    if (sparkCollectors != null) {
      for (var entry : sparkCollectors.entrySet()) {
        String name = entry.getKey();
        GarbageCollector collector = entry.getValue();
        if (name == null || name.isBlank() || collector == null) {
          continue;
        }
        garbageCollectors.put(
            name, new GcTotals(collector.totalCollections(), collector.totalTime()));
      }
    }

    return new Sample(spark, msptMean, msptP95, msptMax, processCpu, garbageCollectors);
  }

  private static Spark availableService() {
    RegisteredServiceProvider<Spark> registration =
        Bukkit.getServicesManager().getRegistration(Spark.class);
    if (registration != null) {
      return registration.getProvider();
    }
    try {
      return SparkProvider.get();
    } catch (IllegalStateException unavailable) {
      return null;
    }
  }
}
