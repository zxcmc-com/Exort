package com.zxcmc.exort.platform;

import java.util.Locale;

public record ModePolicy(
    String configuredMode, boolean resourceMode, String fallbackReason, boolean unknownMode) {
  public static final String DEFAULT_MODE = "RESOURCE";
  public static final String CHORUS_FALLBACK_REASON =
      "Paper's block-updates.disable-chorus-plant-updates is not enabled.";

  public static ModePolicy evaluate(String rawMode, boolean chorusUpdatesDisabled) {
    String configured =
        rawMode == null || rawMode.isBlank() ? DEFAULT_MODE : rawMode.toUpperCase(Locale.ROOT);
    boolean unknown = !configured.equals("VANILLA") && !configured.equals("RESOURCE");
    if (unknown) {
      configured = DEFAULT_MODE;
    }
    boolean resourceMode = configured.equals("RESOURCE");
    if (!resourceMode || chorusUpdatesDisabled) {
      return new ModePolicy(configured, resourceMode, "", unknown);
    }
    return new ModePolicy(configured, false, CHORUS_FALLBACK_REASON, unknown);
  }
}
