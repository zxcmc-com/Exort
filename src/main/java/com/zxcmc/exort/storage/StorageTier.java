package com.zxcmc.exort.storage;

import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public class StorageTier {
  private static final long ITEMS_PER_PAGE = 45L * 64L;
  private static final Map<String, StorageTier> REGISTRY = new LinkedHashMap<>();

  private final String key;
  private long maxItems;
  private Material displayMaterial;
  private String displayName;

  public StorageTier(String key, long maxItems, Material displayMaterial, String displayName) {
    this.key = key;
    this.maxItems = maxItems;
    this.displayMaterial = displayMaterial;
    this.displayName = displayName;
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

  public void setMaxItems(long maxItems) {
    this.maxItems = maxItems;
  }

  public void setDisplayMaterial(Material displayMaterial) {
    this.displayMaterial = displayMaterial;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public static Optional<StorageTier> fromString(String raw) {
    if (raw == null) return Optional.empty();
    return Optional.ofNullable(REGISTRY.get(raw.toLowerCase(Locale.ROOT)));
  }

  public static Collection<StorageTier> allTiers() {
    return Collections.unmodifiableCollection(REGISTRY.values());
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
      String name = tierSec.getString("displayName", key);
      if (name == null || name.trim().isEmpty()) {
        logger.warning("Tier '" + key + "': displayName empty, using key");
        name = key;
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
      if (pages) {
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
