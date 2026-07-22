package com.zxcmc.exort.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable storage-tier lookup owned by one prepared or published runtime generation. */
public final class StorageTierCatalog {
  private static final StorageTierCatalog EMPTY = new StorageTierCatalog(Map.of());

  private final Map<String, StorageTier> tiers;

  StorageTierCatalog(Map<String, StorageTier> tiers) {
    this.tiers = Collections.unmodifiableMap(new LinkedHashMap<>(tiers));
  }

  public Optional<StorageTier> find(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(tiers.get(raw.toLowerCase(Locale.ROOT)));
  }

  public Collection<StorageTier> tiers() {
    return List.copyOf(tiers.values());
  }

  public boolean isEmpty() {
    return tiers.isEmpty();
  }

  /** Stable semantic fingerprint used by transactional reload diagnostics. */
  public String fingerprint() {
    StringBuilder canonical = new StringBuilder();
    tiers.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              StorageTier tier = entry.getValue();
              canonical
                  .append(entry.getKey())
                  .append('|')
                  .append(tier.key())
                  .append('|')
                  .append(tier.maxItems())
                  .append('|')
                  .append(tier.displayMaterial().getKey())
                  .append('|')
                  .append(tier.fallbackDisplayName())
                  .append('|')
                  .append(tier.translationKey().orElse(""))
                  .append('|')
                  .append(tier.color() == null ? "" : tier.color().asHexString())
                  .append('|')
                  .append(tier.isReadOnly())
                  .append('\n');
            });
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  public static StorageTierCatalog parse(ConfigurationSection section, Logger logger) {
    return StorageTier.parseCatalog(section, logger);
  }

  public static StorageTierCatalog empty() {
    return EMPTY;
  }
}
