package com.zxcmc.exort.storage;

import java.util.Optional;

public final class StorageTierResolver {
  private StorageTierResolver() {}

  public record Resolution(StorageTier tier, long tierMaxItems, boolean orphaned) {}

  public static Optional<Resolution> resolve(String tierKey, Long storedMaxItems) {
    return resolve(StorageTierCatalog.active(), tierKey, storedMaxItems);
  }

  public static Optional<Resolution> resolve(
      StorageTierCatalog catalog, String tierKey, Long storedMaxItems) {
    Optional<StorageTier> configured = catalog == null ? Optional.empty() : catalog.find(tierKey);
    if (configured.isPresent()) {
      StorageTier tier = configured.get();
      return Optional.of(new Resolution(tier, tier.maxItems(), false));
    }
    if (tierKey == null || tierKey.isBlank() || storedMaxItems == null || storedMaxItems <= 0) {
      return Optional.empty();
    }
    StorageTier orphaned = StorageTier.orphaned(tierKey, storedMaxItems);
    return Optional.of(new Resolution(orphaned, storedMaxItems, true));
  }
}
