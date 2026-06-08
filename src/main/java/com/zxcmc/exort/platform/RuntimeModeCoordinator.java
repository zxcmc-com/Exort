package com.zxcmc.exort.platform;

import com.zxcmc.exort.carrier.WireCarrierMode;
import com.zxcmc.exort.infra.logging.ExortLog;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class RuntimeModeCoordinator {
  private static final String RESOURCE_BARRIER_WARNING =
      "RESOURCE wire carriers are using BARRIER because Paper chorus-plant updates are enabled;"
          + " wire visuals stay RESOURCE, but wire hitboxes are full blocks.";
  private static final String RESOURCE_FIX_COMMAND_BEFORE = "Run ";
  private static final String RESOURCE_FIX_COMMAND_AFTER =
      " to enable chorus carriers after restart.";

  private RuntimeModeCoordinator() {}

  public static RuntimeModeState evaluate(
      String rawMode, BooleanSupplier chorusUpdatesDisabled, String resourceFixCommand) {
    return evaluate(rawMode, WireCarrierMode.DEFAULT, chorusUpdatesDisabled, resourceFixCommand);
  }

  public static RuntimeModeState evaluate(
      String rawMode,
      WireCarrierMode wireCarrierMode,
      BooleanSupplier chorusUpdatesDisabled,
      String resourceFixCommand) {
    Objects.requireNonNull(chorusUpdatesDisabled, "chorusUpdatesDisabled");
    Objects.requireNonNull(resourceFixCommand, "resourceFixCommand");
    ModePolicy policy =
        ModePolicy.evaluate(rawMode, chorusUpdatesDisabled.getAsBoolean(), wireCarrierMode);
    if (policy.unknownMode()) {
      ExortLog.warn("Unknown mode '" + rawMode + "' in config.yml; using RESOURCE.");
    }
    if (policy.resourceWireCarrierFallback()) {
      ExortLog.warn(RESOURCE_BARRIER_WARNING);
      ExortLog.warnCommand(
          RESOURCE_FIX_COMMAND_BEFORE, resourceFixCommand, RESOURCE_FIX_COMMAND_AFTER);
    }
    return new RuntimeModeState(
        policy.configuredMode(),
        policy.resourceMode(),
        policy.resourceWireUsesBarrier(),
        policy.resourceWireCarrierFallback());
  }

  public static List<String> resourceWireCarrierWarningLines(String resourceFixCommand) {
    Objects.requireNonNull(resourceFixCommand, "resourceFixCommand");
    return List.of(
        RESOURCE_BARRIER_WARNING,
        RESOURCE_FIX_COMMAND_BEFORE + resourceFixCommand + RESOURCE_FIX_COMMAND_AFTER);
  }
}
