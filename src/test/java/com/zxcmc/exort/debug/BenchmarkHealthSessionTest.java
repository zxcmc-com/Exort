package com.zxcmc.exort.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class BenchmarkHealthSessionTest {
  @Test
  void absentProviderIsAQuietUnavailableResult() {
    List<String> warnings = new ArrayList<>();
    BenchmarkHealthSession session =
        new BenchmarkHealthSession(BenchmarkHealthSource.Sample::unavailable, warnings::add);

    session.begin();
    BenchmarkHealthSession.Report report = session.finish();

    assertFalse(report.available());
    assertTrue(warnings.isEmpty());
  }

  @Test
  void stableProviderReportsEndHealthAndGcDelta() {
    Object provider = new Object();
    ArrayDeque<BenchmarkHealthSource.Sample> samples =
        new ArrayDeque<>(
            List.of(
                sample(provider, 10.0, 15.0, 20.0, 0.25, 7L, 30L),
                sample(provider, 12.0, 18.0, 25.0, 0.50, 10L, 47L)));
    BenchmarkHealthSession session =
        new BenchmarkHealthSession(samples::removeFirst, ignored -> {});

    session.begin();
    BenchmarkHealthSession.Report report = session.finish();

    assertTrue(report.available());
    assertEquals(12.0, report.msptMean().orElseThrow());
    assertEquals(18.0, report.msptP95().orElseThrow());
    assertEquals(25.0, report.msptMax().orElseThrow());
    assertEquals(0.50, report.processCpu().orElseThrow());
    assertEquals(3L, report.gcCollections().orElseThrow());
    assertEquals(17L, report.gcTimeMillis().orElseThrow());
  }

  @Test
  void providerChangeKeepsEndHealthButDropsIncomparableGcDelta() {
    Object firstProvider = new Object();
    Object secondProvider = new Object();
    ArrayDeque<BenchmarkHealthSource.Sample> samples =
        new ArrayDeque<>(
            List.of(
                sample(firstProvider, 10.0, 15.0, 20.0, 0.25, 100L, 500L),
                sample(secondProvider, 11.0, 16.0, 21.0, 0.30, 1L, 2L)));
    BenchmarkHealthSession session =
        new BenchmarkHealthSession(samples::removeFirst, ignored -> {});

    session.begin();
    BenchmarkHealthSession.Report report = session.finish();

    assertTrue(report.available());
    assertEquals(11.0, report.msptMean().orElseThrow());
    assertTrue(report.gcCollections().isEmpty());
    assertTrue(report.gcTimeMillis().isEmpty());
  }

  @Test
  void resetOrDecreasedGcCountersClampToZero() {
    Object provider = new Object();
    ArrayDeque<BenchmarkHealthSource.Sample> samples =
        new ArrayDeque<>(
            List.of(
                sample(provider, 10.0, 15.0, 20.0, 0.25, 100L, 500L),
                sample(provider, 11.0, 16.0, 21.0, 0.30, 1L, 2L)));
    BenchmarkHealthSession session =
        new BenchmarkHealthSession(samples::removeFirst, ignored -> {});

    session.begin();
    BenchmarkHealthSession.Report report = session.finish();

    assertEquals(0L, report.gcCollections().orElseThrow());
    assertEquals(0L, report.gcTimeMillis().orElseThrow());
  }

  @Test
  void gcDeltaUsesSaturatingAggregation() {
    Object provider = new Object();
    BenchmarkHealthSource.Sample start =
        new BenchmarkHealthSource.Sample(
            provider,
            OptionalDouble.empty(),
            OptionalDouble.empty(),
            OptionalDouble.empty(),
            OptionalDouble.empty(),
            Map.of(
                "young", new BenchmarkHealthSource.GcTotals(0L, 0L),
                "old", new BenchmarkHealthSource.GcTotals(0L, 0L)));
    BenchmarkHealthSource.Sample end =
        new BenchmarkHealthSource.Sample(
            provider,
            OptionalDouble.empty(),
            OptionalDouble.empty(),
            OptionalDouble.empty(),
            OptionalDouble.empty(),
            Map.of(
                "young", new BenchmarkHealthSource.GcTotals(Long.MAX_VALUE, Long.MAX_VALUE),
                "old", new BenchmarkHealthSource.GcTotals(Long.MAX_VALUE, Long.MAX_VALUE)));
    ArrayDeque<BenchmarkHealthSource.Sample> samples = new ArrayDeque<>(List.of(start, end));
    BenchmarkHealthSession session =
        new BenchmarkHealthSession(samples::removeFirst, ignored -> {});

    session.begin();
    BenchmarkHealthSession.Report report = session.finish();

    assertEquals(Long.MAX_VALUE, report.gcCollections().orElseThrow());
    assertEquals(Long.MAX_VALUE, report.gcTimeMillis().orElseThrow());
  }

  @Test
  void invalidNumericSamplesAreOmitted() {
    BenchmarkHealthSource.Sample sample =
        new BenchmarkHealthSource.Sample(
            new Object(),
            OptionalDouble.of(Double.NaN),
            OptionalDouble.of(Double.POSITIVE_INFINITY),
            OptionalDouble.of(-1.0),
            OptionalDouble.of(1.01),
            Map.of());

    assertTrue(sample.msptMean().isEmpty());
    assertTrue(sample.msptP95().isEmpty());
    assertTrue(sample.msptMax().isEmpty());
    assertTrue(sample.processCpu().isEmpty());
  }

  @Test
  void repeatedProviderFailureWarnsOnceAndDoesNotEscape() {
    List<String> warnings = new ArrayList<>();
    BenchmarkHealthSession session =
        new BenchmarkHealthSession(
            () -> {
              throw new IllegalStateException("broken provider");
            },
            warnings::add);

    session.begin();
    assertFalse(session.finish().available());
    session.begin();
    assertFalse(session.finish().available());

    assertEquals(1, warnings.size());
    assertTrue(warnings.getFirst().contains("IllegalStateException"));
    assertTrue(warnings.getFirst().contains("broken provider"));
  }

  @Test
  void expectedLinkageFailureIsContainedButFatalVmErrorIsNot() {
    BenchmarkHealthSession linkageSession =
        new BenchmarkHealthSession(
            () -> {
              throw new NoClassDefFoundError("spark API removed");
            },
            ignored -> {});
    linkageSession.begin();
    assertFalse(linkageSession.finish().available());

    BenchmarkHealthSession fatalSession =
        new BenchmarkHealthSession(
            () -> {
              throw new OutOfMemoryError("fatal");
            },
            ignored -> {});
    assertThrows(OutOfMemoryError.class, fatalSession::begin);
  }

  private static BenchmarkHealthSource.Sample sample(
      Object provider,
      double mean,
      double p95,
      double max,
      double cpu,
      long collections,
      long timeMillis) {
    return new BenchmarkHealthSource.Sample(
        provider,
        OptionalDouble.of(mean),
        OptionalDouble.of(p95),
        OptionalDouble.of(max),
        OptionalDouble.of(cpu),
        Map.of("collector", new BenchmarkHealthSource.GcTotals(collections, timeMillis)));
  }
}
