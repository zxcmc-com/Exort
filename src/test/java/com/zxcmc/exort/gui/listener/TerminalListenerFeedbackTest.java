package com.zxcmc.exort.gui.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.network.TerminalLinkFinder.StorageSearchStatus;
import org.junit.jupiter.api.Test;

class TerminalListenerFeedbackTest {
  @Test
  void reportsTheLimitThatActuallyStoppedTraversal() {
    assertEquals(64, TerminalListener.traversalLimit(StorageSearchStatus.WIRE_LIMIT, 64, 128));
    assertEquals(128, TerminalListener.traversalLimit(StorageSearchStatus.HARD_CAP, 64, 128));
  }

  @Test
  void ordinarySearchOutcomesDoNotTriggerTraversalFeedback() {
    assertEquals(-1, TerminalListener.traversalLimit(StorageSearchStatus.NONE, 64, 128));
    assertEquals(-1, TerminalListener.traversalLimit(StorageSearchStatus.OK, 64, 128));
    assertEquals(-1, TerminalListener.traversalLimit(StorageSearchStatus.MULTIPLE, 64, 128));
  }
}
