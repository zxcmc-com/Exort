package com.zxcmc.exort.infra.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class ConfigEnums {
  private static final Logger LOGGER = Logger.getLogger("Exort");

  private ConfigEnums() {}

  public static <E extends Enum<E>> E parse(String path, String raw, E defaultValue) {
    Objects.requireNonNull(defaultValue, "defaultValue");
    if (raw == null) {
      return defaultValue;
    }
    Class<E> enumType = defaultValue.getDeclaringClass();
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    if (!normalized.isEmpty()) {
      try {
        return Enum.valueOf(enumType, normalized);
      } catch (IllegalArgumentException ignored) {
        // Fall through to the default warning below.
      }
    }
    warnInvalid(path, raw, defaultValue, enumType);
    return defaultValue;
  }

  private static <E extends Enum<E>> void warnInvalid(
      String path, String raw, E defaultValue, Class<E> enumType) {
    String options =
        Arrays.stream(enumType.getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.joining(", "));
    String configPath = path == null || path.isBlank() ? "<unknown>" : path;
    LOGGER.warning(
        "Invalid config enum value '"
            + raw
            + "' for "
            + configPath
            + "; using "
            + defaultValue.name()
            + ". Allowed values: "
            + options
            + ".");
  }
}
