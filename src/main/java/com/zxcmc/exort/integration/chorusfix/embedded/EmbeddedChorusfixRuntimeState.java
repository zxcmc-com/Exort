package com.zxcmc.exort.integration.chorusfix.embedded;

public record EmbeddedChorusfixRuntimeState(boolean disabled) {
  public static EmbeddedChorusfixRuntimeState known(boolean disabled) {
    return new EmbeddedChorusfixRuntimeState(disabled);
  }

  public boolean disabledTrue() {
    return disabled;
  }
}
