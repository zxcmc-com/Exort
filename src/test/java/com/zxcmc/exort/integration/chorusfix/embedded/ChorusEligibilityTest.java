package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class ChorusEligibilityTest {
  @Test
  void acceptsVanillaEligiblePlant() {
    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(
            ChorusMaterial.CHORUS_PLANT, ChorusFaceMask.parse("down"), Set.of(), false);

    assertTrue(decision.process());
    assertEquals(ChorusEligibility.Reason.VANILLA_ELIGIBLE, decision.reason());
  }

  @Test
  void skipsImpossibleMasksBeforeProviderStateMatters() {
    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(
            ChorusMaterial.CHORUS_PLANT, ChorusFaceMask.parse("north,up,down"), Set.of(), false);

    assertFalse(decision.process());
    assertEquals(ChorusEligibility.Reason.IMPOSSIBLE_MASK, decision.reason());
  }

  @Test
  void acceptsImpossibleMasksInVanillaMutationMode() {
    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(
            ChorusMaterial.CHORUS_PLANT,
            ChorusFaceMask.parse("north,up,down"),
            Set.of(),
            false,
            ChorusEligibility.Mode.VANILLA_MUTATION);

    assertTrue(decision.process());
    assertEquals(ChorusEligibility.Reason.VANILLA_ELIGIBLE, decision.reason());
  }

  @Test
  void vanillaMutationModeStillSkipsIgnoredMasks() {
    ChorusFaceMask mask = ChorusFaceMask.ALL;

    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(
            ChorusMaterial.CHORUS_PLANT,
            mask,
            Set.of(mask),
            false,
            ChorusEligibility.Mode.VANILLA_MUTATION);

    assertFalse(decision.process());
    assertEquals(ChorusEligibility.Reason.IGNORED_MASK, decision.reason());
  }

  @Test
  void ignoredMasksTakePriorityOverProviderClaims() {
    ChorusFaceMask mask = ChorusFaceMask.ALL;

    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(
            ChorusMaterial.CHORUS_PLANT,
            mask,
            Set.of(mask),
            true,
            ChorusEligibility.Mode.VANILLA_MUTATION);

    assertFalse(decision.process());
    assertEquals(ChorusEligibility.Reason.IGNORED_MASK, decision.reason());
  }

  @Test
  void vanillaMutationModeStillSkipsHardProviderClaims() {
    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(
            ChorusMaterial.CHORUS_PLANT,
            ChorusFaceMask.parse("north,up,down"),
            Set.of(),
            true,
            ChorusEligibility.Mode.VANILLA_MUTATION);

    assertFalse(decision.process());
    assertEquals(ChorusEligibility.Reason.PROVIDER_CLAIMED, decision.reason());
  }

  @Test
  void skipsConfiguredIgnoredMasks() {
    ChorusFaceMask mask = ChorusFaceMask.parse("east,down");

    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(ChorusMaterial.CHORUS_PLANT, mask, Set.of(mask), false);

    assertFalse(decision.process());
    assertEquals(ChorusEligibility.Reason.IGNORED_MASK, decision.reason());
  }

  @Test
  void skipsProviderClaimedBlocks() {
    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(
            ChorusMaterial.CHORUS_FLOWER, null, Set.of(ChorusFaceMask.ALL), true);

    assertFalse(decision.process());
    assertEquals(ChorusEligibility.Reason.PROVIDER_CLAIMED, decision.reason());
  }
}
