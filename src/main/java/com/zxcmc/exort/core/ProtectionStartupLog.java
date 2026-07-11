package com.zxcmc.exort.core;

import java.util.Collection;

final class ProtectionStartupLog {
  private ProtectionStartupLog() {}

  static String disabledByConfig() {
    return "[Protection] Integration disabled by config.";
  }

  static String noSupportedProvider() {
    return "[Protection] No supported protection plugin found; using allow-all mode.";
  }

  static String failOpenOverride() {
    return "[Protection] UNSAFE EXPERT OVERRIDE: protection.failClosedOnError=false. "
        + "If an enabled claim provider fails, Exort actions may bypass its protection. "
        + "Use this only for temporary compatibility diagnostics.";
  }

  static String enabled(Collection<String> adapterNames) {
    return "[Protection] Integration enabled: " + String.join(", ", adapterNames);
  }
}
