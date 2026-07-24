package com.zxcmc.exort.api.model;

/**
 * Minimum copy-safety rule for an inspected Exort item.
 *
 * <p>This value is informational and is not an authorization decision. Integrations remain
 * responsible for normal inventory accounting, protection checks, and rollback.
 */
public enum ExortItemCopyPolicy {
  /** The item is a stateless template, but normal inventory accounting is still required. */
  TEMPLATE,

  /**
   * The exact stack carries mutable state. Move it intact; do not reconstruct, merge, or reset it.
   */
  PRESERVE_STATE,

  /** The exact stack carries a unique persistent identity. Never clone, merge, or duplicate it. */
  PRESERVE_UNIQUE_IDENTITY
}
