package com.zxcmc.exort.debug;

import java.util.Arrays;

final class LoadTestVerdictCalculator {
  private static final double TPS_CAP = 20.0;
  private static final double STALL_TPS_THRESHOLD = 12.0;

  private LoadTestVerdictCalculator() {}

  static Result calculate(
      double[] tickMs, double[] msptSamples, int sampleCount, String forcedGradeKey) {
    int n = Math.min(sampleCount, Math.min(tickMs.length, msptSamples.length));
    if (n <= 0) {
      return Result.empty(forcedGradeKey);
    }
    double[] tpsSamples = new double[n];
    double tpsMin = TPS_CAP;
    double tpsMax = 0.0;
    double tpsSum = 0.0;
    int severeStalls = 0;
    for (int i = 0; i < n; i++) {
      double ms = tickMs[i];
      double tps = ms <= 0.0 ? TPS_CAP : Math.min(TPS_CAP, 1000.0 / ms);
      tpsSamples[i] = tps;
      tpsMin = Math.min(tpsMin, tps);
      tpsMax = Math.max(tpsMax, tps);
      tpsSum += tps;
      if (tps < STALL_TPS_THRESHOLD) {
        severeStalls++;
      }
    }
    Arrays.sort(tpsSamples);
    double tpsAvg = tpsSum / n;
    double tpsP1 = percentile(tpsSamples, 0.01);
    double severeStallRatio = severeStalls / (double) n;

    double[] msptSorted = Arrays.copyOf(msptSamples, n);
    Arrays.sort(msptSorted);
    double msptMin = msptSorted[0];
    double msptMax = msptSorted[n - 1];
    double msptAvg = 0.0;
    for (int i = 0; i < n; i++) {
      msptAvg += msptSamples[i];
    }
    msptAvg /= n;
    double msptP95 = percentile(msptSorted, 0.95);
    double msptP99 = percentile(msptSorted, 0.99);

    String gradeKey =
        forcedGradeKey != null
            ? forcedGradeKey
            : gradeKey(tpsAvg, severeStallRatio, msptAvg, msptP95);
    return new Result(
        gradeKey,
        tpsMin,
        tpsMax,
        tpsAvg,
        tpsP1,
        msptMin,
        msptMax,
        msptAvg,
        msptP95,
        msptP99,
        severeStalls);
  }

  private static double percentile(double[] sorted, double percentile) {
    if (sorted.length == 0) return 0.0;
    int idx = Math.max(0, (int) Math.ceil(sorted.length * percentile) - 1);
    return sorted[Math.min(sorted.length - 1, idx)];
  }

  private static String gradeKey(
      double tpsAvg, double severeStallRatio, double msptAvg, double msptP95) {
    if (msptAvg >= 70.0 || msptP95 >= 80.0 || tpsAvg < 15.0 || severeStallRatio >= 0.10) {
      return "message.debug_load_grade_awful";
    }
    if (msptAvg >= 60.0 || msptP95 >= 70.0 || tpsAvg < 16.0 || severeStallRatio >= 0.05) {
      return "message.debug_load_grade_bad";
    }
    if (msptAvg > 50.0 || msptP95 > 60.0 || tpsAvg < 18.0 || severeStallRatio >= 0.02) {
      return "message.debug_load_grade_poor";
    }
    if (msptAvg <= 35.0 && msptP95 <= 45.0 && tpsAvg >= 18.5 && severeStallRatio <= 0.01) {
      return "message.debug_load_grade_good";
    }
    return "message.debug_load_grade_warn";
  }

  record Result(
      String gradeKey,
      double tpsMin,
      double tpsMax,
      double tpsAvg,
      double tpsP1,
      double msptMin,
      double msptMax,
      double msptAvg,
      double msptP95,
      double msptP99,
      int severeStalls) {
    static Result empty(String forcedGradeKey) {
      return new Result(
          forcedGradeKey == null ? "message.debug_load_grade_unknown" : forcedGradeKey,
          0.0,
          0.0,
          0.0,
          0.0,
          0.0,
          0.0,
          0.0,
          0.0,
          0.0,
          0);
    }
  }
}
