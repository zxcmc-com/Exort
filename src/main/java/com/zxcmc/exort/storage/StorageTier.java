package com.zxcmc.exort.storage;

import com.zxcmc.exort.api.model.StorageTierDescriptor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class StorageTier {
  private static final long ITEMS_PER_PAGE = 45L * 64L;
  private static final Map<String, StorageTier> REGISTRY = new LinkedHashMap<>();

  private final String key;
  private final long maxItems;
  private final Material displayMaterial;
  private final String displayName;

  StorageTier(String key, long maxItems, Material displayMaterial, String displayName) {
    if (maxItems <= 0) {
      throw new IllegalArgumentException("maxItems must be positive");
    }
    this.key = Objects.requireNonNull(key, "key");
    this.maxItems = maxItems;
    this.displayMaterial = Objects.requireNonNull(displayMaterial, "displayMaterial");
    this.displayName = Objects.requireNonNull(displayName, "displayName");
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

  public String displayName() {
    return displayName;
  }

  public StorageTierDescriptor descriptor() {
    return new StorageTierDescriptor(
        key, maxItems, displayMaterial.getKey().toString(), displayName);
  }

  public static Optional<StorageTier> fromString(String raw) {
    if (raw == null) return Optional.empty();
    return Optional.ofNullable(REGISTRY.get(raw.toLowerCase(Locale.ROOT)));
  }

  public static Collection<StorageTier> allTiers() {
    return List.copyOf(REGISTRY.values());
  }

  public static void loadFromConfig(ConfigurationSection section, Logger logger) {
    REGISTRY.clear();
    if (section == null) {
      logger.warning("No tiers section found in config; storages will be unusable.");
      return;
    }
    Material fallback = parseMaterialOrNull("STRUCTURE_BLOCK");
    if (fallback == null) fallback = Material.AIR;
    for (String key : section.getKeys(false)) {
      ConfigurationSection tierSec = section.getConfigurationSection(key);
      if (tierSec == null) continue;
      long maxItems = parseMaxItems(tierSec.get("maxItems"), logger, key);
      String matRaw = tierSec.getString("material");
      String name = tierSec.getString("displayName", humanizeTierKey(key));
      if (name == null || name.trim().isEmpty()) {
        name = humanizeTierKey(key);
        logger.warning("Tier '" + key + "': displayName empty, using " + name);
      }
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
      StorageTier tier = new StorageTier(key.toUpperCase(Locale.ROOT), maxItems, mat, name);
      REGISTRY.put(key.toLowerCase(Locale.ROOT), tier);
    }
    if (REGISTRY.isEmpty()) {
      logger.warning("No storage tiers configured; storages will be unusable.");
    }
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
