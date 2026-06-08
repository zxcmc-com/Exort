package com.zxcmc.exort.infra.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResourcePackHostingTest {
  @Test
  void itemsAdderHostingParsesFromConfig() {
    assertEquals(ResourcePackHosting.ITEMSADDER, ResourcePackHosting.fromConfig("ITEMSADDER"));
  }

  @Test
  void invalidHostingFallsBackToAutoAtRuntime() {
    assertEquals(ResourcePackHosting.AUTO, ResourcePackHosting.fromConfig("bad"));
  }

  @Test
  void invalidDeliveryFallsBackToAutoAtRuntime() {
    assertEquals(ResourcePackDelivery.AUTO, ResourcePackDelivery.fromConfig("bad"));
  }

  @Test
  void autoHostingPrefersNexoBeforeItemsAdder() {
    assertEquals(
        ResourcePackHosting.NEXO, ResourcePackService.resolveAutoHosting(true, true, true));
  }

  @Test
  void autoHostingUsesItemsAdderBeforeOfficialPack() {
    assertEquals(
        ResourcePackHosting.ITEMSADDER, ResourcePackService.resolveAutoHosting(false, true, true));
  }

  @Test
  void autoHostingCanUseOfficialPackWithoutProvider() {
    assertEquals(
        ResourcePackHosting.EXORT, ResourcePackService.resolveAutoHosting(false, false, true));
  }

  @Test
  void autoHostingFallsBackToSelfHostWithoutProviderOrOfficialPack() {
    assertEquals(
        ResourcePackHosting.SELFHOST, ResourcePackService.resolveAutoHosting(false, false, false));
  }
}
