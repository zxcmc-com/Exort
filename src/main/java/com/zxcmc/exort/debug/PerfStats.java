package com.zxcmc.exort.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class PerfStats {
  public enum Area {
    BUS("bus.tick"),
    NETWORK("network.find"),
    DISPLAY("display.drain"),
    MONITOR("monitor.refresh"),
    STORAGE_DB("storage-db.write"),
    GUI("gui.build"),
    PLACEMENT_GUARD("placement-guard.tick"),
    WIRELESS("wireless.interact");

    private final String label;

    Area(String label) {
      this.label = label;
    }

    public String label() {
      return label;
    }
  }

  private static final int MAX_SAMPLES_PER_LABEL = 20_000;
  private static final int MAX_TICK_SAMPLES_PER_LABEL = 1_200;
  private static final Map<String, Accumulator> DATA = new ConcurrentHashMap<>();
  private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();
  private static final Map<String, AtomicLong> GAUGES = new ConcurrentHashMap<>();
  private static volatile boolean enabled;
  private static volatile long currentTick = Long.MIN_VALUE;

  private PerfStats() {}

  public static void resetAndEnable() {
    DATA.clear();
    COUNTERS.clear();
    GAUGES.clear();
    currentTick = Long.MIN_VALUE;
    enabled = true;
  }

  public static void disable() {
    enabled = false;
  }

  public static boolean isEnabled() {
    return enabled;
  }

  public static void tick(long tick) {
    if (!enabled || tick == currentTick) {
      return;
    }
    long previous = currentTick;
    currentTick = tick;
    if (previous == Long.MIN_VALUE) {
      return;
    }
    for (Accumulator accumulator : DATA.values()) {
      accumulator.finishTick();
    }
  }

  public static void measure(Area area, Runnable task) {
    measure(area.label(), task);
  }

  public static void measure(String label, Runnable task) {
    if (!enabled) {
      task.run();
      return;
    }
    long start = System.nanoTime();
    try {
      task.run();
    } finally {
      record(label, System.nanoTime() - start);
    }
  }

  public static <T> T measure(Area area, Supplier<T> task) {
    return measure(area.label(), task);
  }

  public static <T> T measure(String label, Supplier<T> task) {
    if (!enabled) {
      return task.get();
    }
    long start = System.nanoTime();
    try {
      return task.get();
    } finally {
      record(label, System.nanoTime() - start);
    }
  }

  public static void record(Area area, long nanos) {
    record(area.label(), nanos);
  }

  public static void record(String label, long nanos) {
    if (!enabled || label == null || label.isBlank() || nanos < 0L) {
      return;
    }
    DATA.computeIfAbsent(label, ignored -> new Accumulator()).record(nanos);
  }

  public static void incrementCounter(String label) {
    addCounter(label, 1L);
  }

  public static void addCounter(String label, long delta) {
    if (!enabled || label == null || label.isBlank() || delta == 0L) {
      return;
    }
    COUNTERS.computeIfAbsent(label, ignored -> new AtomicLong()).addAndGet(delta);
  }

  public static void setGauge(String label, long value) {
    if (!enabled || label == null || label.isBlank()) {
      return;
    }
    GAUGES.computeIfAbsent(label, ignored -> new AtomicLong()).set(value);
  }

  public static Snapshot snapshot() {
    tick(currentTick);
    List<MetricStats> metrics = new ArrayList<>();
    long totalNanos = 0L;
    for (var entry : DATA.entrySet()) {
      MetricStats stats = entry.getValue().snapshot(entry.getKey());
      metrics.add(stats);
      totalNanos += stats.totalNanos();
    }
    metrics.sort(Comparator.comparingLong(MetricStats::totalNanos).reversed());
    return new Snapshot(totalNanos, metrics, snapshotLongs(COUNTERS), snapshotLongs(GAUGES));
  }

  private static Map<String, Long> snapshotLongs(Map<String, AtomicLong> source) {
    Map<String, Long> out = new HashMap<>();
    for (var entry : source.entrySet()) {
      out.put(entry.getKey(), entry.getValue().get());
    }
    return Map.copyOf(out);
  }

  public record Snapshot(
      long totalNanos,
      List<MetricStats> metrics,
      Map<String, Long> counters,
      Map<String, Long> gauges) {
    public boolean hasSamples() {
      return totalNanos > 0L && metrics.stream().anyMatch(metric -> metric.calls() > 0L);
    }
  }

  public record MetricStats(
      String label,
      long calls,
      long totalNanos,
      long maxNanos,
      long p95Nanos,
      long p99Nanos,
      long p95TickNanos,
      long p99TickNanos) {
    public double totalMs() {
      return totalNanos / 1_000_000.0;
    }

    public double avgMicros() {
      return calls <= 0L ? 0.0 : (totalNanos / 1_000.0) / calls;
    }

    public double p95Micros() {
      return p95Nanos / 1_000.0;
    }

    public double p99Micros() {
      return p99Nanos / 1_000.0;
    }

    public double p95TickMicros() {
      return p95TickNanos / 1_000.0;
    }

    public double p99TickMicros() {
      return p99TickNanos / 1_000.0;
    }
  }

  private static final class Accumulator {
    private long calls;
    private long totalNanos;
    private long maxNanos;
    private long currentTickNanos;
    private final List<Long> samples = new ArrayList<>();
    private final List<Long> tickSamples = new ArrayList<>();

    synchronized void record(long nanos) {
      calls++;
      totalNanos += nanos;
      currentTickNanos += nanos;
      maxNanos = Math.max(maxNanos, nanos);
      if (samples.size() < MAX_SAMPLES_PER_LABEL) {
        samples.add(nanos);
      }
    }

    synchronized void finishTick() {
      if (currentTickNanos <= 0L) {
        return;
      }
      if (tickSamples.size() < MAX_TICK_SAMPLES_PER_LABEL) {
        tickSamples.add(currentTickNanos);
      }
      currentTickNanos = 0L;
    }

    synchronized MetricStats snapshot(String label) {
      finishTick();
      List<Long> sorted = new ArrayList<>(samples);
      sorted.sort(Long::compareTo);
      List<Long> sortedTicks = new ArrayList<>(tickSamples);
      sortedTicks.sort(Long::compareTo);
      return new MetricStats(
          label,
          calls,
          totalNanos,
          maxNanos,
          percentile(sorted, 0.95),
          percentile(sorted, 0.99),
          percentile(sortedTicks, 0.95),
          percentile(sortedTicks, 0.99));
    }

    private long percentile(List<Long> sorted, double percentile) {
      if (sorted.isEmpty()) {
        return 0L;
      }
      int idx = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
      return sorted.get(Math.min(sorted.size() - 1, idx));
    }
  }
}
