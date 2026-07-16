package com.zxcmc.exort.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigNumbersTest {
  @Test
  void acceptsBoundsWithoutDiagnostics() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("minimum", 1);
    yaml.set("maximum", 10L);
    yaml.set("decimal", 2.5D);
    CapturingLogger logs = new CapturingLogger();
    ConfigNumbers numbers = new ConfigNumbers(yaml, logs.logger());

    assertEquals(1, numbers.integer("minimum", 5, 1, 10));
    assertEquals(10L, numbers.longInteger("maximum", 5, 1, 10));
    assertEquals(2.5D, numbers.decimal("decimal", 1.0D, 0.0D, 3.0D));
    assertTrue(logs.messages().isEmpty());
  }

  @Test
  void clampsOverflowAndReportsPathRangeAndEffectiveValueOnce() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("limit", Long.MAX_VALUE);
    CapturingLogger logs = new CapturingLogger();
    ConfigNumbers numbers = new ConfigNumbers(yaml, logs.logger());

    assertEquals(64, numbers.integer("limit", 1, 1, 64));
    assertEquals(64, numbers.integer("limit", 1, 1, 64));

    assertEquals(1, logs.messages().size());
    assertTrue(logs.messages().getFirst().contains("limit="));
    assertTrue(logs.messages().getFirst().contains("1..64"));
    assertTrue(logs.messages().getFirst().contains("using 64"));
  }

  @Test
  void rejectsFractionalWrongTypeAndNonFiniteValuesWithFallbacks() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("fractional", 1.5D);
    yaml.set("wrongType", "12");
    yaml.set("nan", Double.NaN);
    yaml.set("infinity", Double.POSITIVE_INFINITY);
    CapturingLogger logs = new CapturingLogger();
    ConfigNumbers numbers = new ConfigNumbers(yaml, logs.logger());

    assertEquals(7, numbers.integer("fractional", 7, 1, 10));
    assertEquals(8L, numbers.longInteger("wrongType", 8L, 1L, 10L));
    assertEquals(3.0D, numbers.decimal("nan", 3.0D, 0.0D, 10.0D));
    assertEquals(4.0D, numbers.decimal("infinity", 4.0D, 0.0D, 10.0D));
    assertEquals(4, logs.messages().size());
  }

  private static final class CapturingLogger {
    private final List<String> messages = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();

    private CapturingLogger() {
      logger.setUseParentHandlers(false);
      logger.setLevel(Level.ALL);
      logger.addHandler(
          new Handler() {
            @Override
            public void publish(LogRecord record) {
              messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          });
    }

    private Logger logger() {
      return logger;
    }

    private List<String> messages() {
      return messages;
    }
  }
}
