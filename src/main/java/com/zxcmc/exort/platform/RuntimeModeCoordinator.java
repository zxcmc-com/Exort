package com.zxcmc.exort.platform;

import com.zxcmc.exort.infra.logging.ExortLog;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class RuntimeModeCoordinator {
  private static final String CHORUS_FIX_COMMAND_BEFORE = "To fix this automatically, run ";
  private static final String CHORUS_FIX_COMMAND_AFTER =
      ". This command will update the Paper option, set Exort mode to RESOURCE, notify players,"
          + " and restart the server after 10 seconds.";

  private RuntimeModeCoordinator() {}

  public static RuntimeModeState evaluate(
      String rawMode, BooleanSupplier chorusUpdatesDisabled, String fixCommand) {
    Objects.requireNonNull(chorusUpdatesDisabled, "chorusUpdatesDisabled");
    Objects.requireNonNull(fixCommand, "fixCommand");
    ModePolicy policy = ModePolicy.evaluate(rawMode, chorusUpdatesDisabled.getAsBoolean());
    if (policy.unknownMode()) {
      ExortLog.warn("Unknown mode '" + rawMode + "' in config.yml; using RESOURCE.");
    }
    if (!policy.fallbackReason().isBlank()) {
      ExortLog.warn(policy.fallbackReason());
      List<String> helpLines = chorusFallbackHelpLines(fixCommand);
      ExortLog.warn(helpLines.get(0));
      ExortLog.warnCommand(CHORUS_FIX_COMMAND_BEFORE, fixCommand, CHORUS_FIX_COMMAND_AFTER);
      ExortLog.warn(helpLines.get(2));
    }
    return new RuntimeModeState(
        policy.configuredMode(), policy.resourceMode(), policy.fallbackReason());
  }

  public static List<String> chorusFallbackHelpLines(String fixCommand) {
    Objects.requireNonNull(fixCommand, "fixCommand");
    return List.of(
        "It is HIGHLY recommended to enable this setting for improved performance and prevent bugs"
            + " with chorus-plants which are used to display wires by default in RESOURCE mode.",
        CHORUS_FIX_COMMAND_BEFORE + fixCommand + CHORUS_FIX_COMMAND_AFTER,
        "Until then, Exort effective mode is VANILLA and resource-pack delivery is disabled.");
  }
}
