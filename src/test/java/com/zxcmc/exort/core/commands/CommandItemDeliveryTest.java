package com.zxcmc.exort.core.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CommandItemDeliveryTest {
  @Test
  void clampAmountKeepsGiveRequestsInSupportedRange() {
    assertEquals(1, CommandItemDelivery.clampAmount(0, 512));
    assertEquals(64, CommandItemDelivery.clampAmount(64, 512));
    assertEquals(512, CommandItemDelivery.clampAmount(1_000, 512));
  }
}
