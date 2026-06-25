package com.zxcmc.exort.integration.chorusfix.embedded;

import java.util.Set;

public final class ChorusEligibility {
  private ChorusEligibility() {}

  public static Decision evaluate(
      ChorusMaterial material,
      ChorusFaceMask mask,
      Set<ChorusFaceMask> ignoredMasks,
      boolean providerClaimed) {
    return evaluate(material, mask, ignoredMasks, providerClaimed, Mode.NORMAL);
  }

  public static Decision evaluate(
      ChorusMaterial material,
      ChorusFaceMask mask,
      Set<ChorusFaceMask> ignoredMasks,
      boolean providerClaimed,
      Mode mode) {
    if (!material.isChorusBlock()) {
      return Decision.skip(Reason.NOT_CHORUS);
    }
    if (mask != null && ignoredMasks.contains(mask)) {
      return Decision.skip(Reason.IGNORED_MASK);
    }
    if (providerClaimed) {
      return Decision.skip(Reason.PROVIDER_CLAIMED);
    }
    if (mask != null && mask.isImpossibleCustomCarrier() && mode != Mode.VANILLA_MUTATION) {
      return Decision.skip(Reason.IMPOSSIBLE_MASK);
    }
    return Decision.accept();
  }

  public record Decision(boolean process, Reason reason) {
    public static Decision accept() {
      return new Decision(true, Reason.VANILLA_ELIGIBLE);
    }

    public static Decision skip(Reason reason) {
      return new Decision(false, reason);
    }
  }

  public enum Reason {
    VANILLA_ELIGIBLE,
    NOT_CHORUS,
    IMPOSSIBLE_MASK,
    IGNORED_MASK,
    PROVIDER_CLAIMED
  }

  public enum Mode {
    NORMAL,
    VANILLA_MUTATION
  }
}
