package com.zxcmc.exort.storage;

import com.zxcmc.exort.api.model.StorageTierDescriptor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class StorageTier {
  private static final long ITEMS_PER_PAGE = 45L * 64L;
  private final String key;
  private final long maxItems;
  private final Material displayMaterial;
  private final String fallbackDisplayName;
  private final String translationKey;
  private final TextColor color;
  private final boolean readOnly;

  StorageTier(
      String key,
      long maxItems,
      Material displayMaterial,
      String fallbackDisplayName,
      String translationKey,
      TextColor color) {
    this(key, maxItems, displayMaterial, fallbackDisplayName, translationKey, color, false);
  }

  private StorageTier(
      String key,
      long maxItems,
      Material displayMaterial,
      String fallbackDisplayName,
      String translationKey,
      TextColor color,
      boolean readOnly) {
    if (maxItems <= 0) {
      throw new IllegalArgumentException("maxItems must be positive");
    }
    this.key = Objects.requireNonNull(key, "key");
    this.maxItems = maxItems;
    this.displayMaterial = Objects.requireNonNull(displayMaterial, "displayMaterial");
    this.fallbackDisplayName = Objects.requireNonNull(fallbackDisplayName, "fallbackDisplayName");
    this.translationKey = translationKey;
    this.color = color;
    this.readOnly = readOnly;
  }

  public String key() {
    return key;
  }

  public long maxItems() {
    return maxItems;
  }

  public Material displayMaterial() {
    return displayMaterial;
  }

  public String fallbackDisplayName() {
    return fallbackDisplayName;
  }

  public Optional<String> translationKey() {
    return Optional.ofNullable(translationKey);
  }

  public TextColor color() {
    return color;
  }

  /** True for a preserved tier snapshot whose configured tier no longer exists. */
  public boolean isReadOnly() {
    return readOnly;
  }

  static StorageTier orphaned(String rawKey, long maxItems) {
    if (rawKey == null || rawKey.isBlank()) {
      throw new IllegalArgumentException("rawKey must not be blank");
    }
    Material material = parseMaterialOrNull("STRUCTURE_BLOCK");
    if (material == null) material = Material.AIR;
    String normalized = rawKey.trim().toUpperCase(Locale.ROOT);
    return new StorageTier(
        normalized,
        maxItems,
        material,
        humanizeTierKey(normalized) + " (orphaned)",
        null,
        NamedTextColor.RED,
        true);
  }

  /** Returns an immutable read-only view without changing the configured tier catalog. */
  public static StorageTier readOnlySnapshot(StorageTier source) {
    Objects.requireNonNull(source, "source");
    if (source.readOnly) {
      return source;
    }
    return new StorageTier(
        source.key,
        source.maxItems,
        source.displayMaterial,
        source.fallbackDisplayName,
        source.translationKey,
        source.color,
        true);
  }

  public StorageTierDescriptor descriptor() {
    return new StorageTierDescriptor(
        key, maxItems, displayMaterial.getKey().toString(), fallbackDisplayName);
  }

  public static Optional<StorageTier> fromString(String raw) {
    return StorageTierCatalog.active().find(raw);
  }

  public static Collection<StorageTier> allTiers() {
    return StorageTierCatalog.active().tiers();
  }

  public static boolean loadFromConfig(ConfigurationSection section, Logger logger) {
    try {
      StorageTierCatalog.publish(parseCatalog(section, logger));
      return true;
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  static StorageTierCatalog parseCatalog(ConfigurationSection section, Logger logger) {
    Objects.requireNonNull(logger, "logger");
    if (section == null) {
      logger.severe("No tiers section found; keeping the last valid storage tier catalog.");
      throw new IllegalArgumentException("No tiers section found");
    }
    Map<String, StorageTier> candidate = new LinkedHashMap<>();
    Material fallback = parseMaterialOrNull("STRUCTURE_BLOCK");
    if (fallback == null) fallback = Material.AIR;
    for (String key : section.getKeys(false)) {
      ConfigurationSection tierSec = section.getConfigurationSection(key);
      if (tierSec == null) {
        logger.severe(
            "Tier '" + key + "' is not a configuration section; keeping the last valid catalog.");
        throw new IllegalArgumentException("Tier is not a configuration section: " + key);
      }
      long maxItems = parseMaxItems(tierSec.get("maxItems"), logger, key);
      String matRaw = tierSec.getString("material");
      NameData name = parseName(tierSec, logger, key);
      TextColor color = parseColor(tierSec.getString("color"), logger, key);
      Material mat = null;
      if (matRaw == null || matRaw.isBlank()) {
        logger.warning("Tier '" + key + "': material not set, using fallback " + fallback);
        mat = fallback;
      } else {
        mat = parseMaterialOrNull(matRaw);
        if (mat == null) {
          logger.warning(
              "Tier '" + key + "': unknown material '" + matRaw + "', using fallback " + fallback);
          mat = fallback;
        }
      }
      StorageTier tier =
          new StorageTier(
              key.toUpperCase(Locale.ROOT),
              maxItems,
              mat,
              name.fallbackDisplayName(),
              name.translationKey(),
              color);
      String normalizedKey = key.toLowerCase(Locale.ROOT);
      if (candidate.putIfAbsent(normalizedKey, tier) != null) {
        logger.severe(
            "Storage tier key '"
                + key
                + "' duplicates another key after normalization; keeping the last valid catalog.");
        throw new IllegalArgumentException("Duplicate normalized tier key: " + key);
      }
    }
    if (candidate.isEmpty()) {
      logger.severe("No storage tiers configured; keeping the last valid storage tier catalog.");
      throw new IllegalArgumentException("No storage tiers configured");
    }
    return new StorageTierCatalog(candidate);
  }

  static void resetForTests() {
    StorageTierCatalog.resetForTests();
  }

  static String humanizeTierKey(String key) {
    if (key == null || key.isBlank()) {
      return "Storage";
    }
    String[] parts = key.trim().toLowerCase(Locale.ROOT).split("[_\\-\\s]+");
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      if (!builder.isEmpty()) {
        builder.append(' ');
      }
      builder.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        builder.append(part.substring(1));
      }
    }
    return builder.isEmpty() ? "Storage" : builder.toString();
  }

  private static NameData parseName(ConfigurationSection tierSec, Logger logger, String tierKey) {
    String raw = tierSec.getString("name");
    if (raw == null || raw.trim().isEmpty()) {
      String fallback = humanizeTierKey(tierKey);
      logger.warning("Tier '" + tierKey + "': name empty, using " + fallback);
      return new NameData(fallback, null);
    }
    String trimmed = raw.trim();
    String translationKey = placeholderKey(trimmed);
    if (translationKey != null) {
      return new NameData(humanizeTranslationKey(translationKey), translationKey);
    }
    return new NameData(trimmed, null);
  }

  private static String placeholderKey(String raw) {
    if (raw.length() < 3 || raw.charAt(0) != '{' || raw.charAt(raw.length() - 1) != '}') {
      return null;
    }
    String key = raw.substring(1, raw.length() - 1).trim();
    if (key.isEmpty() || key.indexOf('{') >= 0 || key.indexOf('}') >= 0) {
      return null;
    }
    return key;
  }

  private static String humanizeTranslationKey(String key) {
    if (key == null || key.isBlank()) {
      return "Storage";
    }
    int dot = key.lastIndexOf('.');
    String tail = dot >= 0 && dot + 1 < key.length() ? key.substring(dot + 1) : key;
    return humanizeTierKey(tail);
  }

  private static TextColor parseColor(String raw, Logger logger, String tierKey) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim();
    String candidate = value;
    if (candidate.startsWith("<") && candidate.endsWith(">") && candidate.length() > 2) {
      candidate = candidate.substring(1, candidate.length() - 1).trim();
    }
    if (candidate.regionMatches(true, 0, "color:", 0, "color:".length())) {
      candidate = candidate.substring("color:".length()).trim();
    }
    TextColor color = null;
    if (candidate.startsWith("#")) {
      color = TextColor.fromHexString(candidate);
    }
    if (color == null) {
      color = NamedTextColor.NAMES.value(candidate.toLowerCase(Locale.ROOT).replace('-', '_'));
    }
    if (color == null) {
      logger.warning("Tier '" + tierKey + "': invalid color '" + value + "', using no color");
    }
    return color;
  }

  private record NameData(String fallbackDisplayName, String translationKey) {}

  private static long parseMaxItems(Object raw, Logger logger, String tierKey) {
    long defaultValue = ITEMS_PER_PAGE * 5L;
    if (raw == null) {
      logger.warning("Tier '" + tierKey + "': maxItems not set, using default " + defaultValue);
      return defaultValue;
    }
    if (raw instanceof Number num) {
      long value = num.longValue();
      if (value <= 0) {
        logger.warning(
            "Tier '" + tierKey + "': maxItems must be positive, using default " + defaultValue);
        return defaultValue;
      }
      return Math.max(1L, value);
    }
    String str = raw.toString().trim();
    if (str.isEmpty()) {
      logger.warning("Tier '" + tierKey + "': maxItems empty, using default " + defaultValue);
      return defaultValue;
    }
    boolean pages = str.toLowerCase(Locale.ROOT).endsWith("p");
    String base = pages ? str.substring(0, str.length() - 1).trim() : str;
    try {
      long value = Long.parseLong(base);
      if (value <= 0) {
        logger.warning(
            "Tier '" + tierKey + "': maxItems must be positive, using default " + defaultValue);
        return defaultValue;
      }
      if (pages) {
        if (value > Long.MAX_VALUE / ITEMS_PER_PAGE) {
          logger.warning(
              "Tier '"
                  + tierKey
                  + "': maxItems page value overflows, using default "
                  + defaultValue);
          return defaultValue;
        }
        return Math.max(1L, value * ITEMS_PER_PAGE);
      }
      return Math.max(1L, value);
    } catch (NumberFormatException e) {
      logger.warning(
          "Tier '" + tierKey + "': invalid maxItems '" + str + "', using default " + defaultValue);
      return defaultValue;
    }
  }

  private static Material parseMaterialOrNull(String raw) {
    if (raw == null) return null;
    String id = raw.trim();
    if (id.isEmpty()) return null;
    int colon = id.indexOf(':');
    if (colon >= 0 && colon + 1 < id.length()) {
      id = id.substring(colon + 1);
    }
    id = id.trim().toUpperCase(Locale.ROOT);
    try {
      return Material.valueOf(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
