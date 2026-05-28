package com.zxcmc.exort.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GiveCommandTest {
  @Test
  void storageUsageCommandKeepsPlayerNameInPrefix() {
    assertEquals(
        "/exort give phantomfighterxx storage <tier> [amount]",
        GiveCommand.storageUsageCommand("phantomfighterxx"));
  }
}
