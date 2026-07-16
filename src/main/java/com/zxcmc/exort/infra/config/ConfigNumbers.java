package com.zxcmc.exort.infra.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** Internal typed numeric reader with bounded values and activation-scoped diagnostics. */
public final class ConfigNumbers {
  private final ConfigurationSection config;
  private final Logger logger;
  private final Set<String> warnings = new HashSet<>();

  public ConfigNumbers(ConfigurationSection config, Logger logger) {
    this.config = Objects.requireNonNull(config, "config");
    this.logger = logger;
  }

  public int integer(String path, int fallback, int minimum, int maximum) {
    if (minimum > maximum) {
      throw new IllegalArgumentException("minimum exceeds maximum for " + path);
    }
    int safeFallback = Math.max(minimum, Math.min(maximum, fallback));
    Object raw = config.get(path);
    if (raw == null) {
      return safeFallback;
    }
    BigDecimal decimal = decimal(raw);
    if (decimal == null) {
      warn(path, raw, "an integer in " + minimum + ".." + maximum, safeFallback);
      return safeFallback;
    }
    if (decimal.compareTo(BigDecimal.valueOf(minimum)) < 0) {
      warn(path, raw, "an integer in " + minimum + ".." + maximum, minimum);
      return minimum;
    }
    if (decimal.compareTo(BigDecimal.valueOf(maximum)) > 0) {
      warn(path, raw, "an integer in " + minimum + ".." + maximum, maximum);
      return maximum;
    }
    try {
      return decimal.intValueExact();
    } catch (ArithmeticException ignored) {
      warn(path, raw, "an integer in " + minimum + ".." + maximum, safeFallback);
      return safeFallback;
    }
  }

  public long longInteger(String path, long fallback, long minimum, long maximum) {
    if (minimum > maximum) {
      throw new IllegalArgumentException("minimum exceeds maximum for " + path);
    }
    long safeFallback = Math.max(minimum, Math.min(maximum, fallback));
    Object raw = config.get(path);
    if (raw == null) {
      return safeFallback;
    }
    BigDecimal decimal = decimal(raw);
    if (decimal == null) {
      warn(path, raw, "an integer in " + minimum + ".." + maximum, safeFallback);
      return safeFallback;
    }
    if (decimal.compareTo(BigDecimal.valueOf(minimum)) < 0) {
      warn(path, raw, "an integer in " + minimum + ".." + maximum, minimum);
      return minimum;
    }
    if (decimal.compareTo(BigDecimal.valueOf(maximum)) > 0) {
      warn(path, raw, "an integer in " + minimum + ".." + maximum, maximum);
      return maximum;
    }
    try {
      return decimal.longValueExact();
    } catch (ArithmeticException ignored) {
      warn(path, raw, "an integer in " + minimum + ".." + maximum, safeFallback);
      return safeFallback;
    }
  }

  public double decimal(String path, double fallback, double minimum, double maximum) {
    if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
      throw new IllegalArgumentException("invalid decimal bounds for " + path);
    }
    double safeFallback = Math.max(minimum, Math.min(maximum, fallback));
    Object raw = config.get(path);
    if (raw == null) {
      return safeFallback;
    }
    if (!(raw instanceof Number number)) {
      warn(path, raw, "a finite number in " + minimum + ".." + maximum, safeFallback);
      return safeFallback;
    }
    double value = number.doubleValue();
    if (!Double.isFinite(value)) {
      warn(path, raw, "a finite number in " + minimum + ".." + maximum, safeFallback);
      return safeFallback;
    }
    double bounded = Math.max(minimum, Math.min(maximum, value));
    if (Double.compare(value, bounded) != 0) {
      warn(path, raw, "a finite number in " + minimum + ".." + maximum, bounded);
    }
    return bounded;
  }

  private BigDecimal decimal(Object raw) {
    if (!(raw instanceof Number number)) {
      return null;
    }
    try {
      if (number instanceof BigDecimal decimal) {
        return decimal;
      }
      if (number instanceof BigInteger integer) {
        return new BigDecimal(integer);
      }
      return new BigDecimal(number.toString());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private void warn(String path, Object raw, String expected, Object effective) {
    if (logger == null) {
      return;
    }
    String warning =
        path
            + "="
            + String.valueOf(raw)
            + " is invalid; expected "
            + expected
            + "; using "
            + effective
            + ".";
    if (warnings.add(warning)) {
      logger.warning(warning);
    }
  }
}
