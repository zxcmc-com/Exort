package com.zxcmc.exort.infra.resourcepack.hosting;

import static com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackService.ConfigurationGateDecision.DISCONNECT;
import static com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackService.ConfigurationGateDecision.IGNORE;
import static com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackService.ConfigurationGateDecision.SEND_CONFIGURATION_PACK;
import static com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackService.ConfigurationGateDecision.WAIT_FOR_READY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class ResourcePackConfigurationGateTest {
  @Test
  void directAutoDeliveryWithoutServerPackEnablesConfigurationGate() {
    assertTrue(
        ResourcePackService.configurationGateEnabled(
            ResourcePackHosting.EXORT, ResourcePackDelivery.AUTO, false));
  }

  @Test
  void autoDeliveryWithServerPackKeepsJoinFallbackUngated() {
    assertFalse(
        ResourcePackService.configurationGateEnabled(
            ResourcePackHosting.EXORT, ResourcePackDelivery.AUTO, true));
  }

  @Test
  void explicitConfigurationDeliveryEnablesGateEvenWithServerPack() {
    assertTrue(
        ResourcePackService.configurationGateEnabled(
            ResourcePackHosting.SELFHOST, ResourcePackDelivery.CONFIGURATION, true));
  }

  @Test
  void providerAndManualOrJoinDeliveryAreNotConfigurationGated() {
    assertFalse(
        ResourcePackService.configurationGateEnabled(
            ResourcePackHosting.NEXO, ResourcePackDelivery.CONFIGURATION, false));
    assertFalse(
        ResourcePackService.configurationGateEnabled(
            ResourcePackHosting.EXORT, ResourcePackDelivery.JOIN, false));
    assertFalse(
        ResourcePackService.configurationGateEnabled(
            ResourcePackHosting.EXORT, ResourcePackDelivery.MANUAL, false));
  }

  @Test
  void startingResolvingAndUploadingDirectConfigurationStatesWaitForReady() {
    assertEquals(WAIT_FOR_READY, decision(state("STARTING", false, ResourcePackDelivery.MANUAL)));
    assertEquals(WAIT_FOR_READY, decision(state("RESOLVING", false, ResourcePackDelivery.MANUAL)));
    assertEquals(WAIT_FOR_READY, decision(state("UPLOADING", false, ResourcePackDelivery.MANUAL)));
  }

  @Test
  void readyDirectConfigurationStateSendsPackDuringConfiguration() {
    assertEquals(
        SEND_CONFIGURATION_PACK,
        decision(state("READY", true, ResourcePackDelivery.CONFIGURATION)));
  }

  @Test
  void errorOrUnsendableReadyStatesDisconnectBeforeWorldJoin() {
    assertEquals(DISCONNECT, decision(state("ERROR", false, ResourcePackDelivery.MANUAL)));
    assertEquals(DISCONNECT, decision(state("READY", false, ResourcePackDelivery.MANUAL)));
  }

  @Test
  void joinManualProviderAndVanillaLikeStatesAreIgnoredByConfigurationGate() {
    assertEquals(
        IGNORE, decision(ungated("READY", ResourcePackHosting.EXORT, ResourcePackDelivery.JOIN)));
    assertEquals(
        IGNORE, decision(ungated("READY", ResourcePackHosting.EXORT, ResourcePackDelivery.MANUAL)));
    assertEquals(
        IGNORE,
        decision(ungated("READY", ResourcePackHosting.ITEMSADDER, ResourcePackDelivery.MANUAL)));
    assertEquals(
        IGNORE,
        decision(ungated("READY", ResourcePackHosting.ORAXEN, ResourcePackDelivery.MANUAL)));
    assertEquals(
        IGNORE,
        decision(ungated("DISABLED", ResourcePackHosting.DISABLED, ResourcePackDelivery.MANUAL)));
  }

  private static ResourcePackService.ConfigurationGateDecision decision(
      ResourcePackService.State state) {
    return ResourcePackService.configurationGateDecision(state);
  }

  private static ResourcePackService.State state(
      String status, boolean dispatchReady, ResourcePackDelivery effectiveDelivery) {
    return new ResourcePackService.State(
        status,
        ResourcePackHosting.AUTO,
        ResourcePackHosting.EXORT,
        ResourcePackDelivery.AUTO,
        effectiveDelivery,
        null,
        null,
        dispatchReady ? "https://example.com/exort.zip" : null,
        dispatchReady ? "0123456789abcdef0123456789abcdef01234567" : null,
        null,
        "ERROR".equals(status) ? "failed" : null,
        dispatchReady,
        true,
        true,
        Component.empty(),
        30,
        false);
  }

  private static ResourcePackService.State ungated(
      String status, ResourcePackHosting effective, ResourcePackDelivery effectiveDelivery) {
    return new ResourcePackService.State(
        status,
        ResourcePackHosting.AUTO,
        effective,
        ResourcePackDelivery.AUTO,
        effectiveDelivery,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        false,
        null,
        30,
        false);
  }
}
