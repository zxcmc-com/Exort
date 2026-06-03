package com.zxcmc.exort.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LocalizationFiles {
  public static final String DEFAULT_LANGUAGE = "en_us";
  public static final String RUSSIAN_LANGUAGE = "ru_ru";
  public static final String LANG_DIR = "lang/";
  public static final String LANG_EXT = ".yml";
  public static final String LANG_INDEX = LANG_DIR + "index.yml";
  public static final String CLIENT_KEY_PREFIX = "exort.";

  private LocalizationFiles() {}

  public static YamlConfiguration readYaml(InputStream in) {
    YamlConfiguration cfg =
        YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    configureYaml(cfg);
    return cfg;
  }

  public static YamlConfiguration readYaml(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return readYaml(in);
    }
  }

  public static void configureYaml(YamlConfiguration cfg) {
    cfg.options().width(4096);
  }

  public static List<String> readLanguageIndex(InputStream in) {
    YamlConfiguration cfg = readYaml(in);
    List<String> result = new ArrayList<>();
    for (String code : cfg.getStringList("languages")) {
      String normalized = normalizeLanguage(code);
      if (!normalized.isBlank() && !result.contains(normalized)) {
        result.add(normalized);
      }
    }
    return result;
  }

  public static List<String> readLanguageIndex(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return readLanguageIndex(in);
    }
  }

  public static Map<String, String> readFlatLanguage(InputStream in) {
    return flatEntries(readYaml(in));
  }

  public static Map<String, String> readFlatLanguage(Path path) throws IOException {
    return flatEntries(readYaml(path));
  }

  public static Map<String, String> flatEntries(YamlConfiguration cfg) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String key : cfg.getKeys(true)) {
      if (cfg.isConfigurationSection(key)) {
        continue;
      }
      String value = cfg.getString(key);
      if (value != null) {
        result.put(key, value);
      }
    }
    return result;
  }

  public static boolean isClientResourcePackKey(String key) {
    return key != null && (key.startsWith("item.") || key.startsWith("lore."));
  }

  public static byte[] clientResourcePackJson(Map<String, String> language) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    boolean first = true;
    for (Map.Entry<String, String> entry : language.entrySet()) {
      String key = entry.getKey();
      if (!isClientResourcePackKey(key)) {
        continue;
      }
      if (!first) {
        json.append(",\n");
      }
      first = false;
      json.append("  ")
          .append(jsonString(CLIENT_KEY_PREFIX + key))
          .append(": ")
          .append(jsonString(serverToResourcePack(entry.getValue())));
    }
    json.append("\n}\n");
    return json.toString().getBytes(StandardCharsets.UTF_8);
  }

  public static String serverToResourcePack(String value) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '%') {
        out.append("%%");
        continue;
      }
      if (c == '{') {
        int end = i + 1;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
          end++;
        }
        if (end > i + 1 && end < value.length() && value.charAt(end) == '}') {
          try {
            int index = Integer.parseInt(value.substring(i + 1, end));
            out.append('%').append(index + 1).append("$s");
            i = end;
            continue;
          } catch (NumberFormatException ignored) {
            // Keep malformed placeholder-like text literal instead of failing pack export.
          }
        }
      }
      out.append(c);
    }
    return out.toString();
  }

  public static String normalizeLanguage(String input) {
    if (input == null || input.isBlank()) {
      return DEFAULT_LANGUAGE;
    }
    return input.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
  }

  private static String jsonString(String value) {
    StringBuilder out = new StringBuilder(value.length() + 2);
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
    return out.toString();
  }
}
