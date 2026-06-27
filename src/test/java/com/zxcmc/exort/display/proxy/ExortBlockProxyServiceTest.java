package com.zxcmc.exort.display.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zxcmc.exort.carrier.ChorusPlantVisualState;
import com.zxcmc.exort.display.culling.DisplayCullingConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExortBlockProxyServiceTest {
  private static final DisplayCullingConfig.BlockProxyConfig CONFIG =
      new DisplayCullingConfig.BlockProxyConfig(true, 10.0, 2.0, 3.0, 1.0, 50).normalized();
  private static final String ALL_FALSE_CHORUS_STATE =
      "down=false,east=false,north=false,south=false,up=false,west=false";
  private static final String ALL_TRUE_CHORUS_STATE =
      "down=true,east=true,north=true,south=true,up=true,west=true";

  @Test
  void proxyDecisionUsesConfiguredTransitionBand() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(false, 7.9, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 8.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 7.9, 1.0, CONFIG));
  }

  @Test
  void forceRealDistanceWinsForRealAndProxiedBlocks() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(false, 1.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 1.0, 1.0, CONFIG));
  }

  @Test
  void prepareChangeRequiresSentChunkAndCandidateForProxying() {
    assertFalse(ExortBlockProxyService.shouldPrepareChange(false, false, true, true));
    assertTrue(ExortBlockProxyService.shouldPrepareChange(true, false, true, true));
    assertFalse(ExortBlockProxyService.shouldPrepareChange(true, false, false, true));
    assertTrue(ExortBlockProxyService.shouldPrepareChange(true, true, false, false));
  }

  @Test
  void proxiedCounterTracksOnlyStateTransitions() {
    int count = ExortBlockProxyService.proxiedCountAfterTransition(0, false, true);

    assertEquals(1, count);
    assertEquals(1, ExortBlockProxyService.proxiedCountAfterTransition(count, true, true));
    assertEquals(0, ExortBlockProxyService.proxiedCountAfterTransition(count, true, false));
    assertEquals(0, ExortBlockProxyService.proxiedCountAfterCandidateRemoval(0, true));
  }

  @Test
  void resourcePackDefinesEveryProxyVisualWithoutRotations() throws Exception {
    JsonObject variants = chorusPlantVariants();

    for (ExortBlockProxyService.ProxyVisual visual : ExortBlockProxyService.ProxyVisual.values()) {
      JsonObject entry = variants.getAsJsonObject(visual.stateKey());
      assertNotNull(entry, visual.name());
      assertEquals(visual.modelId(), entry.get("model").getAsString(), visual.name());
      assertFalse(entry.has("x"), visual.name());
      assertFalse(entry.has("y"), visual.name());
    }
  }

  @Test
  void chorusPlantPackKeepsVanillaFallbackAndHiddenParticleState() throws Exception {
    JsonObject variants = chorusPlantVariants();

    assertEquals("block/chorus_plant", modelFor(variants, ALL_FALSE_CHORUS_STATE));
    assertEquals(ChorusPlantVisualState.NONE.stateKey(), ALL_TRUE_CHORUS_STATE);
    assertEquals(ChorusPlantVisualState.NONE.modelId(), modelFor(variants, ALL_TRUE_CHORUS_STATE));
  }

  private static JsonObject chorusPlantVariants() throws Exception {
    String source =
        Files.readString(
            Path.of("src/main/resources/pack/assets/minecraft/blockstates/chorus_plant.json"));
    return JsonParser.parseString(source).getAsJsonObject().getAsJsonObject("variants");
  }

  private static String modelFor(JsonObject variants, String state) {
    return variants.getAsJsonObject(state).get("model").getAsString();
  }
}
