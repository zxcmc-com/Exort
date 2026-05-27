package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InfoButtonStateTest {
  @Test
  void revealStorageIdDoesNotStartConfirmation() {
    InfoButtonState state = new InfoButtonState(1_000);

    state.revealStorageId();

    assertTrue(state.showStorageId());
    assertFalse(state.isConfirming(100));
    assertEquals(0, state.confirmRemaining(100));
  }

  @Test
  void confirmationCountsDownAndExpires() {
    InfoButtonState state = new InfoButtonState(1_000);

    state.startConfirm(10_000);

    assertTrue(state.isConfirming(10_500));
    assertEquals(4, state.confirmRemaining(10_500));
    assertEquals(3, state.decrementConfirm(10_600));
    assertEquals(2, state.decrementConfirm(10_700));
    assertEquals(1, state.decrementConfirm(10_800));
    assertEquals(0, state.decrementConfirm(10_900));
    assertFalse(state.isConfirming(10_900));
  }

  @Test
  void timeoutResetsConfirmation() {
    InfoButtonState state = new InfoButtonState(1_000);

    state.startConfirm(10_000);

    assertFalse(state.isConfirming(11_001));
    assertEquals(0, state.confirmRemaining(11_001));
  }

  @Test
  void resetClearsConfirmationButKeepsRevealedStorageId() {
    InfoButtonState state = new InfoButtonState(1_000);
    state.revealStorageId();
    state.startConfirm(10_000);

    state.resetConfirm();

    assertTrue(state.showStorageId());
    assertFalse(state.isConfirming(10_100));
  }

  @Test
  void blockedStateUsesConfirmTimeout() {
    InfoButtonState state = new InfoButtonState(1_000);
    state.startConfirm(10_000);

    state.markBlocked(10_500);

    assertFalse(state.isConfirming(10_500));
    assertTrue(state.isBlocked(11_499));
    assertFalse(state.isBlocked(11_500));
  }

  @Test
  void zeroTimeoutNeverReportsBlocked() {
    InfoButtonState state = new InfoButtonState(0);

    state.markBlocked(10_000);

    assertFalse(state.isBlocked(10_000));
  }
}
