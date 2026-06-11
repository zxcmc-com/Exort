package com.zxcmc.exort.integration.protection;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record ProtectionStatus(
    Mode mode,
    boolean enabled,
    boolean failClosedOnError,
    List<String> activeAdapters,
    List<String> missingPlugins,
    List<String> failedAdapters,
    List<String> runtimeFailures) {
  public ProtectionStatus {
    Objects.requireNonNull(mode, "mode");
    activeAdapters = sortedCopy(activeAdapters);
    missingPlugins = sortedCopy(missingPlugins);
    failedAdapters = sortedCopy(failedAdapters);
    runtimeFailures = sortedCopy(runtimeFailures);
  }

  public static ProtectionStatus disabledByConfig() {
    return new ProtectionStatus(
        Mode.DISABLED_BY_CONFIG, false, false, List.of(), List.of(), List.of(), List.of());
  }

  public static ProtectionStatus noProvider(
      boolean failClosedOnError, Collection<String> missingPlugins) {
    return new ProtectionStatus(
        Mode.ALLOW_ALL_NO_PROVIDER,
        true,
        failClosedOnError,
        List.of(),
        List.copyOf(missingPlugins),
        List.of(),
        List.of());
  }

  public static ProtectionStatus active(
      boolean failClosedOnError,
      Collection<String> activeAdapters,
      Collection<String> missingPlugins,
      Collection<String> failedAdapters) {
    Mode mode = failedAdapters == null || failedAdapters.isEmpty() ? Mode.ACTIVE : Mode.DEGRADED;
    return new ProtectionStatus(
        mode,
        true,
        failClosedOnError,
        List.copyOf(activeAdapters),
        List.copyOf(missingPlugins),
        List.copyOf(failedAdapters),
        List.of());
  }

  public static ProtectionStatus degradedFailClosed(
      boolean failClosedOnError,
      Collection<String> activeAdapters,
      Collection<String> missingPlugins,
      Collection<String> failedAdapters) {
    return new ProtectionStatus(
        Mode.DEGRADED_FAIL_CLOSED,
        true,
        failClosedOnError,
        List.copyOf(activeAdapters),
        List.copyOf(missingPlugins),
        List.copyOf(failedAdapters),
        List.of());
  }

  public ProtectionStatus withRuntimeFailures(Collection<String> runtimeFailures) {
    if (runtimeFailures == null || runtimeFailures.isEmpty()) {
      return this;
    }
    Mode nextMode = mode == Mode.ACTIVE ? Mode.DEGRADED : mode;
    return new ProtectionStatus(
        nextMode,
        enabled,
        failClosedOnError,
        activeAdapters,
        missingPlugins,
        failedAdapters,
        List.copyOf(runtimeFailures));
  }

  public boolean degraded() {
    return mode == Mode.DEGRADED || mode == Mode.DEGRADED_FAIL_CLOSED || !runtimeFailures.isEmpty();
  }

  private static List<String> sortedCopy(Collection<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values.stream().filter(Objects::nonNull).sorted(String.CASE_INSENSITIVE_ORDER).toList();
  }

  public enum Mode {
    DISABLED_BY_CONFIG,
    ALLOW_ALL_NO_PROVIDER,
    ACTIVE,
    DEGRADED,
    DEGRADED_FAIL_CLOSED
  }
}
