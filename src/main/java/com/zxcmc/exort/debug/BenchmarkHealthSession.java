package com.zxcmc.exort.debug;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.function.Consumer;

/** Main-thread-confined start/end measurement state for optional server health metrics. */
final class BenchmarkHealthSession {
  private final BenchmarkHealthSource source;
  private final Consumer<String> warning;

  private BenchmarkHealthSource.Sample baseline = BenchmarkHealthSource.Sample.unavailable();
  private boolean warned;

  BenchmarkHealthSession(BenchmarkHealthSource source, Consumer<String> warning) {
    this.source = Objects.requireNonNull(source, "source");
    this.warning = Objects.requireNonNull(warning, "warning");
  }

  void begin() {
    baseline = safeCapture();
  }

  Report finish() {
    BenchmarkHealthSource.Sample start = baseline;
    BenchmarkHealthSource.Sample end = safeCapture();
    baseline = BenchmarkHealthSource.Sample.unavailable();
    return Report.between(start, end);
  }

  void reset() {
    baseline = BenchmarkHealthSource.Sample.unavailable();
  }

  private BenchmarkHealthSource.Sample safeCapture() {
    try {
      BenchmarkHealthSource.Sample sample = source.capture();
      return sample == null ? BenchmarkHealthSource.Sample.unavailable() : sample;
    } catch (RuntimeException | LinkageError failure) {
      warnOnce(failure);
      return BenchmarkHealthSource.Sample.unavailable();
    }
  }

  private void warnOnce(Throwable failure) {
    if (warned) {
      return;
    }
    warned = true;
    String detail = failure.getMessage();
    String suffix = detail == null || detail.isBlank() ? "" : ": " + detail;
    try {
      warning.accept(
          "spark benchmark health metrics are unavailable ("
              + failure.getClass().getSimpleName()
              + suffix
              + ")");
    } catch (RuntimeException ignored) {
      // Diagnostics must never make the benchmark fail.
    }
  }

  record Report(
      boolean available,
      OptionalDouble msptMean,
      OptionalDouble msptP95,
      OptionalDouble msptMax,
      OptionalDouble processCpu,
      OptionalLong gcCollections,
      OptionalLong gcTimeMillis) {
    Report {
      msptMean = Objects.requireNonNull(msptMean, "msptMean");
      msptP95 = Objects.requireNonNull(msptP95, "msptP95");
      msptMax = Objects.requireNonNull(msptMax, "msptMax");
      processCpu = Objects.requireNonNull(processCpu, "processCpu");
      gcCollections = Objects.requireNonNull(gcCollections, "gcCollections");
      gcTimeMillis = Objects.requireNonNull(gcTimeMillis, "gcTimeMillis");
    }

    static Report between(BenchmarkHealthSource.Sample start, BenchmarkHealthSource.Sample end) {
      if (end == null || !end.available()) {
        return unavailable();
      }
      OptionalLong collections = OptionalLong.empty();
      OptionalLong timeMillis = OptionalLong.empty();
      if (start != null
          && start.available()
          && start.providerIdentity() == end.providerIdentity()) {
        GcDelta delta = gcDelta(start.garbageCollectors(), end.garbageCollectors());
        if (delta.comparable()) {
          collections = OptionalLong.of(delta.collections());
          timeMillis = OptionalLong.of(delta.timeMillis());
        }
      }
      return new Report(
          true,
          end.msptMean(),
          end.msptP95(),
          end.msptMax(),
          end.processCpu(),
          collections,
          timeMillis);
    }

    static Report unavailable() {
      return new Report(
          false,
          OptionalDouble.empty(),
          OptionalDouble.empty(),
          OptionalDouble.empty(),
          OptionalDouble.empty(),
          OptionalLong.empty(),
          OptionalLong.empty());
    }

    private static GcDelta gcDelta(
        Map<String, BenchmarkHealthSource.GcTotals> start,
        Map<String, BenchmarkHealthSource.GcTotals> end) {
      long collections = 0L;
      long timeMillis = 0L;
      boolean comparable = false;
      for (var entry : end.entrySet()) {
        BenchmarkHealthSource.GcTotals before = start.get(entry.getKey());
        if (before == null) {
          continue;
        }
        comparable = true;
        BenchmarkHealthSource.GcTotals after = entry.getValue();
        collections =
            saturatedAdd(collections, nonNegativeDelta(before.collections(), after.collections()));
        timeMillis =
            saturatedAdd(timeMillis, nonNegativeDelta(before.timeMillis(), after.timeMillis()));
      }
      return new GcDelta(comparable, collections, timeMillis);
    }

    private static long nonNegativeDelta(long before, long after) {
      return after >= before ? after - before : 0L;
    }

    private static long saturatedAdd(long left, long right) {
      if (right > Long.MAX_VALUE - left) {
        return Long.MAX_VALUE;
      }
      return left + right;
    }
  }

  private record GcDelta(boolean comparable, long collections, long timeMillis) {}
}
