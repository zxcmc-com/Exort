package com.zxcmc.exort.storage;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable storage-tier lookup published atomically for the active runtime generation. */
public final class StorageTierCatalog {
  private static final StorageTierCatalog EMPTY = new StorageTierCatalog(Map.of());
  private static final AtomicReference<StorageTierCatalog> ACTIVE = new AtomicReference<>(EMPTY);

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

  public static StorageTierCatalog parse(ConfigurationSection section, Logger logger) {
    return StorageTier.parseCatalog(section, logger);
  }

  public static StorageTierCatalog active() {
    return ACTIVE.get();
  }

  public static void publish(StorageTierCatalog catalog) {
    ACTIVE.set(Objects.requireNonNull(catalog, "catalog"));
  }

  static void resetForTests() {
    ACTIVE.set(EMPTY);
  }
}
