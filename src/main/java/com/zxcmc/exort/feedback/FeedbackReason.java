package com.zxcmc.exort.feedback;

/** Player-facing feedback policy for denied or failed interactions. */
public enum FeedbackReason {
  STORAGE_LOADING(Delivery.ACTION_BAR, Severity.WARNING, 900L),
  STORAGE_CLAIM_CONFLICT(Delivery.ACTION_BAR, Severity.ERROR, 1_500L),
  STORAGE_FAILURE(Delivery.CHAT, Severity.ERROR, 3_000L),
  NETWORK_TRAVERSAL_LIMIT(Delivery.ACTION_BAR, Severity.WARNING, 900L),
  INTERACTION_DENIED(Delivery.ACTION_BAR, Severity.ERROR, 900L),
  PLACEMENT_DENIED(Delivery.ACTION_BAR, Severity.ERROR, 900L),
  CHUNK_LOADER_INITIALIZING(Delivery.ACTION_BAR, Severity.WARNING, 900L),
  CHUNK_LOADER_QUOTA(Delivery.ACTION_BAR, Severity.WARNING, 1_500L),
  WIRELESS_ACCESS(Delivery.ACTION_BAR, Severity.ERROR, 900L),
  TRANSMITTER_INPUT(Delivery.ACTION_BAR, Severity.ERROR, 900L),
  OPERATION_FAILURE(Delivery.CHAT, Severity.ERROR, 3_000L);

  private final Delivery delivery;
  private final Severity severity;
  private final long cooldownMillis;

  FeedbackReason(Delivery delivery, Severity severity, long cooldownMillis) {
    this.delivery = delivery;
    this.severity = severity;
    this.cooldownMillis = cooldownMillis;
  }

  Delivery delivery() {
    return delivery;
  }

  Severity severity() {
    return severity;
  }

  long cooldownMillis() {
    return cooldownMillis;
  }

  enum Delivery {
    ACTION_BAR,
    CHAT
  }

  enum Severity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
  }
}
