package com.zxcmc.exort.debug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

@FunctionalInterface
interface BenchmarkHealthSource {
  Sample capture();

  record Sample(
      Object providerIdentity,
      OptionalDouble msptMean,
      OptionalDouble msptP95,
      OptionalDouble msptMax,
      OptionalDouble processCpu,
      Map<String, GcTotals> garbageCollectors) {
    public Sample {
      msptMean = finiteNonNegative(msptMean);
      msptP95 = finiteNonNegative(msptP95);
      msptMax = finiteNonNegative(msptMax);
      processCpu = finiteRange(processCpu, 0.0, 1.0);
      garbageCollectors = immutableGcTotals(garbageCollectors);
      if (providerIdentity == null) {
        msptMean = OptionalDouble.empty();
        msptP95 = OptionalDouble.empty();
        msptMax = OptionalDouble.empty();
        processCpu = OptionalDouble.empty();
        garbageCollectors = Map.of();
      }
    }

    static Sample unavailable() {
      return new Sample(
          null,
          OptionalDouble.empty(),
          OptionalDouble.empty(),
          OptionalDouble.empty(),
          OptionalDouble.empty(),
          Map.of());
    }

    boolean available() {
      return providerIdentity != null;
    }

    private static OptionalDouble finiteNonNegative(OptionalDouble value) {
      return finiteRange(value, 0.0, Double.MAX_VALUE);
    }

    private static OptionalDouble finiteRange(OptionalDouble value, double min, double max) {
      if (value == null || value.isEmpty()) {
        return OptionalDouble.empty();
      }
      double candidate = value.getAsDouble();
      if (!Double.isFinite(candidate) || candidate < min || candidate > max) {
        return OptionalDouble.empty();
      }
      return OptionalDouble.of(candidate);
    }

    private static Map<String, GcTotals> immutableGcTotals(Map<String, GcTotals> source) {
      if (source == null || source.isEmpty()) {
        return Map.of();
      }
      Map<String, GcTotals> copy = new LinkedHashMap<>();
      for (var entry : source.entrySet()) {
        String name = entry.getKey();
        GcTotals totals = entry.getValue();
        if (name == null || name.isBlank() || totals == null) {
          continue;
        }
        copy.put(name, totals);
      }
      return Map.copyOf(copy);
    }
  }

  record GcTotals(long collections, long timeMillis) {
    public GcTotals {
      collections = Math.max(0L, collections);
      timeMillis = Math.max(0L, timeMillis);
    }
  }
}
