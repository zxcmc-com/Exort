package com.zxcmc.exort.runtime;

/** Signals that a failed runtime construction could not fully release its partial resources. */
final class RuntimeConstructionException extends RuntimeException {
  RuntimeConstructionException(Throwable constructionFailure, Throwable cleanupFailure) {
    super("Failed runtime construction left resources with uncertain cleanup", constructionFailure);
    addSuppressed(cleanupFailure);
  }
}
