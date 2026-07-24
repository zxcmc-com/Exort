package com.zxcmc.exort.api.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable public description of an Exort item.
 *
 * <p>The optional variant is exposed only for Storage tiers and Wireless Booster tiers. Persistent
 * identities, owners, charge, links, and marker state are intentionally not exposed.
 *
 * @param type stable content type
 * @param variantKey normalized Storage or Wireless Booster tier, when applicable
 * @param copyPolicy minimum copy-safety rule for the exact stack
 */
public record ExortItemDescriptor(
    ExortContentType type, Optional<String> variantKey, ExortItemCopyPolicy copyPolicy) {
  /** Validates and defensively normalizes the immutable descriptor. */
  public ExortItemDescriptor {
    Objects.requireNonNull(type, "type");
    variantKey =
        Objects.requireNonNull(variantKey, "variantKey")
            .map(String::trim)
            .filter(value -> !value.isEmpty());
    Objects.requireNonNull(copyPolicy, "copyPolicy");
  }

  /**
   * Returns whether the item carries a unique persistent identity.
   *
   * @return whether cloning or merging this exact item is unsafe
   */
  public boolean hasPersistentIdentity() {
    return copyPolicy == ExortItemCopyPolicy.PRESERVE_UNIQUE_IDENTITY;
  }
}
