package com.zxcmc.exort.storage;

import java.util.Comparator;
import java.util.Optional;

public final class StorageTierResolver {
  private StorageTierResolver() {}

  public record Resolution(
      StorageTier tier, long tierMaxItems, boolean fallback, boolean missingSnapshot) {}

  public static Optional<Resolution> resolve(String tierKey, Long storedMaxItems) {
    Optional<StorageTier> configured = StorageTier.fromString(tierKey);
    if (configured.isPresent()) {
      StorageTier tier = configured.get();
      return Optional.of(new Resolution(tier, tier.maxItems(), false, false));
    }
    if (StorageTier.allTiers().isEmpty()) {
      return Optional.empty();
    }
    StorageTier fallback = fallbackTier(storedMaxItems);
    return Optional.of(new Resolution(fallback, fallback.maxItems(), true, storedMaxItems == null));
  }

  private static StorageTier fallbackTier(Long storedMaxItems) {
    if (storedMaxItems == null || storedMaxItems <= 0) {
      return smallestTier();
    }
    Optional<StorageTier> exact =
        StorageTier.allTiers().stream()
            .filter(tier -> tier.maxItems() == storedMaxItems)
            .findFirst();
    if (exact.isPresent()) {
      return exact.get();
    }
    Optional<StorageTier> lower =
        StorageTier.allTiers().stream()
            .filter(tier -> tier.maxItems() < storedMaxItems)
            .max(Comparator.comparingLong(StorageTier::maxItems));
    return lower.orElseGet(StorageTierResolver::smallestTier);
  }

  private static StorageTier smallestTier() {
    return StorageTier.allTiers().stream()
        .min(Comparator.comparingLong(StorageTier::maxItems))
        .orElseThrow();
  }
}
