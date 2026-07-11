package com.zxcmc.exort.storage;

/** Typed durable uniqueness conflict for explicit placement-denial/repair handling. */
public final class StorageClaimConflictException extends IllegalStateException {
  public enum Kind {
    STORAGE_ID,
    POSITION,
    UNKNOWN
  }

  private final Kind kind;

  public StorageClaimConflictException(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind == null ? Kind.UNKNOWN : kind;
  }

  public Kind kind() {
    return kind;
  }
}
