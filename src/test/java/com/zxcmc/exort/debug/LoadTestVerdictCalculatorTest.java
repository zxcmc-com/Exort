package com.zxcmc.exort.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LoadTestVerdictCalculatorTest {
  @Test
  void rareWallClockOutliersDoNotForceAwful() {
    double[] tickMs = samples(300, 50.0);
    tickMs[0] = 90.0;
    tickMs[1] = 90.0;
    double[] mspt = samples(300, 30.0);

    LoadTestVerdictCalculator.Result result =
        LoadTestVerdictCalculator.calculate(tickMs, mspt, tickMs.length, null);

    assertEquals("message.debug_load_grade_good", result.gradeKey());
    assertEquals(2, result.severeStalls());
  }

  @Test
  void frequentWallClockStallsGiveAwful() {
    double[] tickMs = samples(300, 50.0);
    for (int i = 0; i < 30; i++) {
      tickMs[i] = 90.0;
    }
    double[] mspt = samples(300, 30.0);

    LoadTestVerdictCalculator.Result result =
        LoadTestVerdictCalculator.calculate(tickMs, mspt, tickMs.length, null);

    assertEquals("message.debug_load_grade_awful", result.gradeKey());
  }

  @Test
  void highMsptP95WorsensGrade() {
    double[] tickMs = samples(100, 50.0);
    double[] mspt = samples(100, 30.0);
    for (int i = 90; i < mspt.length; i++) {
      mspt[i] = 75.0;
    }

    LoadTestVerdictCalculator.Result result =
        LoadTestVerdictCalculator.calculate(tickMs, mspt, tickMs.length, null);

    assertEquals("message.debug_load_grade_bad", result.gradeKey());
  }

  @Test
  void normalRunGivesGood() {
    double[] tickMs = samples(120, 50.0);
    double[] mspt = samples(120, 30.0);

    LoadTestVerdictCalculator.Result result =
        LoadTestVerdictCalculator.calculate(tickMs, mspt, tickMs.length, null);

    assertEquals("message.debug_load_grade_good", result.gradeKey());
  }

  private static double[] samples(int count, double value) {
    double[] samples = new double[count];
    for (int i = 0; i < count; i++) {
      samples[i] = value;
    }
    return samples;
  }
}
