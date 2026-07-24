package com.zxcmc.exort.api.model;

import java.util.Objects;

/**
 * Immutable public description of a loaded Exort block.
 *
 * @param type stable content type
 * @param chorusCarrier whether the real carrier block is {@code CHORUS_PLANT}
 */
public record ExortBlockDescriptor(ExortContentType type, boolean chorusCarrier) {
  /** Validates the immutable descriptor. */
  public ExortBlockDescriptor {
    Objects.requireNonNull(type, "type");
  }
}
