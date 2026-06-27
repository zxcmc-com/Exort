package com.zxcmc.exort.infra.resourcepack.hosting;

import static com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackService.OnlineSendDecision.SEND_NOW;
import static com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackService.OnlineSendDecision.SKIP;
import static com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackService.OnlineSendDecision.WAIT_FOR_READY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class ResourcePackOnlineSendDecisionTest {
  @Test
  void directReadyHostingSendsNow() {
    assertEquals(
        SEND_NOW,
        decision(state("READY", ResourcePackHosting.EXORT, ResourcePackDelivery.AUTO, true)));
    assertEquals(
        SEND_NOW,
        decision(state("READY", ResourcePackHosting.SELFHOST, ResourcePackDelivery.JOIN, true)));
    assertEquals(
        SEND_NOW,
        decision(
            state("READY", ResourcePackHosting.LOBFILE, ResourcePackDelivery.CONFIGURATION, true)));
  }

  @Test
  void directPreparingStatesWaitForReady() {
    assertEquals(
        WAIT_FOR_READY,
        decision(
            state("STARTING", ResourcePackHosting.SELFHOST, ResourcePackDelivery.AUTO, false)));
    assertEquals(
        WAIT_FOR_READY,
        decision(state("RESOLVING", ResourcePackHosting.EXORT, ResourcePackDelivery.AUTO, false)));
    assertEquals(
        WAIT_FOR_READY,
        decision(
            state("UPLOADING", ResourcePackHosting.LOBFILE, ResourcePackDelivery.AUTO, false)));
  }

  @Test
  void providerManagedHostingSkipsExortSideOnlineSend() {
    assertEquals(
        SKIP, decision(state("READY", ResourcePackHosting.NEXO, ResourcePackDelivery.AUTO, false)));
    assertEquals(
        SKIP,
        decision(
            state("STARTING", ResourcePackHosting.ITEMSADDER, ResourcePackDelivery.AUTO, false)));
    assertEquals(
        SKIP,
        decision(state("READY", ResourcePackHosting.ORAXEN, ResourcePackDelivery.AUTO, false)));
  }

  @Test
  void manualDeliverySkipsExortSideOnlineSend() {
    assertEquals(
        SKIP,
        decision(state("READY", ResourcePackHosting.EXORT, ResourcePackDelivery.MANUAL, true)));
    assertEquals(
        SKIP,
        decision(
            state("RESOLVING", ResourcePackHosting.EXORT, ResourcePackDelivery.MANUAL, false)));
  }

  @Test
  void disabledErrorAndUnsendableReadyStatesSkip() {
    assertEquals(
        SKIP,
        decision(
            ResourcePackService.State.disabled(
                "Effective mode is VANILLA", ResourcePackHosting.AUTO, ResourcePackDelivery.AUTO)));
    assertEquals(
        SKIP,
        decision(state("READY", ResourcePackHosting.EXORT, ResourcePackDelivery.AUTO, false)));
    assertEquals(
        SKIP,
        decision(state("ERROR", ResourcePackHosting.EXORT, ResourcePackDelivery.AUTO, false)));
  }

  private static ResourcePackService.OnlineSendDecision decision(ResourcePackService.State state) {
    return ResourcePackService.onlineTransitionSendDecision(state);
  }

  private static ResourcePackService.State state(
      String status,
      ResourcePackHosting effective,
      ResourcePackDelivery configuredDelivery,
      boolean dispatchReady) {
    return new ResourcePackService.State(
        status,
        ResourcePackHosting.AUTO,
        effective,
        configuredDelivery,
        dispatchReady ? configuredDelivery : ResourcePackDelivery.MANUAL,
        null,
        null,
        dispatchReady ? "https://example.com/exort.zip" : null,
        dispatchReady ? "0123456789abcdef0123456789abcdef01234567" : null,
        null,
        "ERROR".equals(status) ? "failed" : null,
        dispatchReady,
        false,
        true,
        Component.empty(),
        30,
        false);
  }
}
