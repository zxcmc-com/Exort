package com.zxcmc.exort.integration.chorusfix.embedded;

public enum EmbeddedChorusfixStatus {
  EMBEDDED("message.mode_chorusfix_status_embedded"),
  EXTERNAL("message.mode_chorusfix_status_external"),
  DISABLED("message.mode_chorusfix_status_disabled"),
  INACTIVE("message.mode_chorusfix_status_inactive"),
  BLOCKED_BY_PROVIDER("message.mode_chorusfix_status_blocked_by_provider");

  private final String messageKey;

  EmbeddedChorusfixStatus(String messageKey) {
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
