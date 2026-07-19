package com.zxcmc.exort.storage;

import com.zxcmc.exort.i18n.Lang;
import java.util.LinkedHashSet;
import java.util.Set;

/** Canonical normalization for persisted and player-entered Storage names. */
public final class StorageNameNormalizer {
  public static final int MAX_LENGTH = 64;
  private static final String STORAGE_KEY = "item.storage";
  private static final String DEFAULT_STORAGE_LABEL = "Storage";

  private StorageNameNormalizer() {}

  public static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder(raw.length());
    raw.codePoints()
        .filter(codePoint -> !Character.isISOControl(codePoint))
        .forEach(builder::appendCodePoint);
    String trimmed = builder.toString().trim();
    if (trimmed.isBlank()) {
      return null;
    }
    int codePoints = trimmed.codePointCount(0, trimmed.length());
    if (codePoints <= MAX_LENGTH) {
      return trimmed;
    }
    int end = trimmed.offsetByCodePoints(0, MAX_LENGTH);
    return trimmed.substring(0, end);
  }

  public static String normalizeAnvilInput(Lang lang, String raw) {
    String normalized = normalize(raw);
    if (normalized == null) {
      return null;
    }
    for (String label : storageLabels(lang)) {
      String stripped = stripPrefix(normalized, label);
      if (stripped != null) {
        return normalize(stripped);
      }
    }
    return normalized;
  }

  private static Set<String> storageLabels(Lang lang) {
    Set<String> labels = new LinkedHashSet<>();
    labels.add(DEFAULT_STORAGE_LABEL);
    if (lang != null) {
      labels.add(storageLabel(lang, null));
      labels.add(storageLabel(lang, "en_us"));
      labels.add(storageLabel(lang, "ru_ru"));
    }
    return labels;
  }

  private static String storageLabel(Lang lang, String language) {
    return lang == null ? DEFAULT_STORAGE_LABEL : lang.trLanguage(language, STORAGE_KEY);
  }

  private static String stripPrefix(String value, String label) {
    String normalizedLabel = normalize(label);
    if (value == null || normalizedLabel == null || value.length() <= normalizedLabel.length()) {
      return null;
    }
    if (!value.regionMatches(true, 0, normalizedLabel, 0, normalizedLabel.length())) {
      return null;
    }
    int pos = normalizedLabel.length();
    while (pos < value.length() && Character.isWhitespace(value.charAt(pos))) {
      pos++;
    }
    if (pos >= value.length() || value.charAt(pos) != ':') {
      return null;
    }
    return value.substring(pos + 1);
  }
}
