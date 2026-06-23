package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zxcmc.exort.carrier.ChorusPlantVisualState;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ExortBlockProxyServiceTest {
  private static final DisplayCullingConfig.BlockProxyConfig CONFIG =
      new DisplayCullingConfig.BlockProxyConfig(true, 64.0, 2.0, 6.0, 8.0, 1200).normalized();
  private static final String ALL_FALSE_CHORUS_STATE =
      "down=false,east=false,north=false,south=false,up=false,west=false";
  private static final String NATURAL_VERTICAL_CHORUS_STATE =
      "down=true,east=false,north=false,south=false,up=true,west=false";
  private static final String ALL_TRUE_CHORUS_STATE =
      "down=true,east=true,north=true,south=true,up=true,west=true";
  private static final List<String> UNCLAIMED_IMPOSSIBLE_CHORUS_STATES =
      List.of(
          "down=true,east=false,north=true,south=false,up=true,west=false",
          "down=true,east=true,north=false,south=false,up=true,west=false",
          "down=true,east=false,north=false,south=true,up=true,west=false",
          "down=true,east=false,north=false,south=false,up=true,west=true",
          "down=true,east=true,north=true,south=false,up=true,west=false",
          "down=true,east=false,north=true,south=true,up=true,west=false",
          "down=true,east=false,north=true,south=false,up=true,west=true",
          "down=true,east=true,north=false,south=true,up=true,west=false",
          "down=true,east=true,north=false,south=false,up=true,west=true",
          "down=true,east=false,north=false,south=true,up=true,west=true",
          "down=true,east=true,north=true,south=true,up=true,west=false",
          "down=true,east=false,north=true,south=true,up=true,west=true");

  @Test
  void proxiesAtSingleTransitionBeforeRenderEdge() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(false, 61.9, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 62.0, 1.0, CONFIG));
  }

  @Test
  void restoresProxiedBlocksInsideDisplayRenderRange() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(true, 62.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 61.9, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 8.0, 1.0, CONFIG));
  }

  @Test
  void forceRealDistanceWinsOverProxyState() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(true, 8.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.REAL,
        ExortBlockProxyService.decideVisual(false, 8.0, 1.0, CONFIG));
  }

  @Test
  void usesLowerBlockViewRangeMultiplier() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(false, 42.7, 0.7, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 42.8, 0.7, CONFIG));
  }

  @Test
  void realBlocksDoNotStayRealInsideTransitionBand() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 63.0, 1.0, CONFIG));
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(true, 63.0, 1.0, CONFIG));
  }

  @Test
  void clampsUnsafeMultiplier() {
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 12.0, -1.0, CONFIG));
  }

  @Test
  void skipsProxyChangeWhenChunkWasNotSentToPlayer() {
    assertFalse(ExortBlockProxyService.shouldPrepareChange(false, false, true, true));
  }

  @Test
  void allowsProxyChangeOnlyForSentTrackedCandidates() {
    assertTrue(ExortBlockProxyService.shouldPrepareChange(true, false, true, true));
    assertFalse(ExortBlockProxyService.shouldPrepareChange(true, false, false, true));
    assertFalse(ExortBlockProxyService.shouldPrepareChange(true, false, true, false));
  }

  @Test
  void skipsRestoreChangeWhenChunkWasNotSentToPlayer() {
    assertFalse(ExortBlockProxyService.shouldPrepareChange(false, true, false, false));
    assertTrue(ExortBlockProxyService.shouldPrepareChange(true, true, false, false));
  }

  @Test
  void proxiedCounterTracksProxyAndRestoreTransitions() {
    int count = 0;

    count = ExortBlockProxyService.proxiedCountAfterTransition(count, false, true);
    assertEquals(1, count);
    count = ExortBlockProxyService.proxiedCountAfterTransition(count, true, true);
    assertEquals(1, count);
    count = ExortBlockProxyService.proxiedCountAfterTransition(count, true, false);
    assertEquals(0, count);
    count = ExortBlockProxyService.proxiedCountAfterTransition(count, false, false);
    assertEquals(0, count);
  }

  @Test
  void proxiedCounterTracksForgetOfProxiedCandidatesOnly() {
    assertEquals(2, ExortBlockProxyService.proxiedCountAfterCandidateRemoval(3, true));
    assertEquals(3, ExortBlockProxyService.proxiedCountAfterCandidateRemoval(3, false));
    assertEquals(0, ExortBlockProxyService.proxiedCountAfterCandidateRemoval(0, true));
  }

  @Test
  void proxyDistanceIncludesPlayerHeight() {
    double distance = ExortBlockProxyService.visualDistanceToBlock(0.5, 62.5, 0.5, 0, 0, 0);
    assertEquals(62.0, distance, 0.0001);
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, distance, 1.0, CONFIG));
  }

  @Test
  void restoreBufferDoesNotMoveTransitionFarInsideVisibleRange() {
    DisplayCullingConfig.BlockProxyConfig config =
        new DisplayCullingConfig.BlockProxyConfig(true, 64.0, 2.0, 12.0, 8.0, 1200).normalized();
    assertEquals(
        ExortBlockProxyService.VisualDecision.KEEP,
        ExortBlockProxyService.decideVisual(false, 61.9, 1.0, config));
    assertEquals(
        ExortBlockProxyService.VisualDecision.PROXY,
        ExortBlockProxyService.decideVisual(false, 62.0, 1.0, config));
  }

  @Test
  void usesDedicatedChorusStatesForProxyModels() {
    assertEquals(
        ChorusPlantVisualState.TERMINAL_MONITOR_BUS_PROXY.stateKey(),
        ExortBlockProxyService.ProxyVisual.TERMINAL_MONITOR_BUS.stateKey());
    assertEquals(
        ChorusPlantVisualState.STORAGE_PROXY.stateKey(),
        ExortBlockProxyService.ProxyVisual.STORAGE.stateKey());
  }

  @Test
  void proxyStatesAreUniqueAndNotReserved() {
    for (ExortBlockProxyService.ProxyVisual visual : ExortBlockProxyService.ProxyVisual.values()) {
      assertFalse(visual.stateKey().contains("down=false"));
      assertFalse(
          visual.stateKey().equals("down=true,east=true,north=true,south=true,up=true,west=true"));
    }
    assertFalse(
        ExortBlockProxyService.ProxyVisual.TERMINAL_MONITOR_BUS
            .stateKey()
            .equals(ExortBlockProxyService.ProxyVisual.STORAGE.stateKey()));
  }

  @Test
  void resourcePackDefinesEveryProxyVisual() throws Exception {
    JsonObject variants = chorusPlantVariants();

    for (ExortBlockProxyService.ProxyVisual visual : ExortBlockProxyService.ProxyVisual.values()) {
      JsonObject entry = variants.getAsJsonObject(visual.stateKey());
      assertNotNull(entry, visual.name());
      assertEquals(visual.modelId(), entry.get("model").getAsString(), visual.name());
      assertFalse(entry.has("x"), visual.name());
      assertFalse(entry.has("y"), visual.name());
    }
    assertNotNull(variants.getAsJsonObject(ALL_TRUE_CHORUS_STATE));
  }

  @Test
  void resourcePackDefinesWireParticleStateAsHiddenGlassModel() throws Exception {
    JsonObject variants = chorusPlantVariants();

    assertEquals(ALL_TRUE_CHORUS_STATE, ChorusPlantVisualState.NONE.stateKey());
    assertEquals(ChorusPlantVisualState.NONE.modelId(), modelFor(variants, ALL_TRUE_CHORUS_STATE));
  }

  @Test
  void resourcePackClaimsOnlyActiveExortChorusStates() throws Exception {
    JsonObject variants = chorusPlantVariants();

    assertEquals("block/chorus_plant", modelFor(variants, ALL_FALSE_CHORUS_STATE));
    assertEquals(ChorusPlantVisualState.NONE.modelId(), modelFor(variants, ALL_TRUE_CHORUS_STATE));
    assertEquals(1, countModels(variants, ChorusPlantVisualState.NONE.modelId()));
    assertNull(variants.getAsJsonObject(NATURAL_VERTICAL_CHORUS_STATE));
    for (String state : UNCLAIMED_IMPOSSIBLE_CHORUS_STATES) {
      assertNull(variants.getAsJsonObject(state), state);
    }
  }

  @Test
  void proxyModelUsesLeftTerminalTextureOnEveryFace() throws Exception {
    String source =
        Files.readString(Path.of("src/main/resources/pack/assets/exort/models/proxy.json"));
    JsonObject root = JsonParser.parseString(source).getAsJsonObject();

    assertEquals("exort:block/terminal", root.getAsJsonObject("textures").get("0").getAsString());
    assertEquals(
        "exort:breaking/particles/block",
        root.getAsJsonObject("textures").get("particle").getAsString());
    JsonObject faces =
        root.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
    for (String face : List.of("north", "east", "south", "west", "up", "down")) {
      JsonObject faceData = faces.getAsJsonObject(face);
      assertEquals("#0", faceData.get("texture").getAsString(), face);
      assertEquals(0.0, faceData.getAsJsonArray("uv").get(0).getAsDouble(), 0.0001, face);
      assertEquals(0.0, faceData.getAsJsonArray("uv").get(1).getAsDouble(), 0.0001, face);
      assertEquals(8.0, faceData.getAsJsonArray("uv").get(2).getAsDouble(), 0.0001, face);
      assertEquals(16.0, faceData.getAsJsonArray("uv").get(3).getAsDouble(), 0.0001, face);
    }
  }

  @Test
  void proxyParticleModelsUseCroppedBreakingTextures() throws Exception {
    assertModelParticle(
        "src/main/resources/pack/assets/exort/models/proxy.json", "exort:breaking/particles/block");
    assertModelParticle(
        "src/main/resources/pack/assets/exort/models/storage/storage.json",
        "exort:breaking/particles/storage");
    assertModelParticle(
        "src/main/resources/pack/assets/exort/models/none.json", "exort:breaking/particles/wire");
  }

  @Test
  void breakingParticleTexturesAreCroppedFromSourceTextures() throws Exception {
    assertCroppedTexture(
        "src/main/resources/pack/assets/exort/textures/block/terminal.png",
        "src/main/resources/pack/assets/exort/textures/breaking/particles/block.png",
        0,
        0,
        16,
        16);
    assertCroppedTexture(
        "src/main/resources/pack/assets/exort/textures/block/storage.png",
        "src/main/resources/pack/assets/exort/textures/breaking/particles/storage.png",
        0,
        0,
        16,
        16);
    assertCroppedTexture(
        "src/main/resources/pack/assets/exort/textures/block/wire.png",
        "src/main/resources/pack/assets/exort/textures/breaking/particles/wire.png",
        5,
        5,
        6,
        6);
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

  private static long countModels(JsonObject variants, String expected) {
    return variants.entrySet().stream()
        .filter(
            entry -> expected.equals(entry.getValue().getAsJsonObject().get("model").getAsString()))
        .count();
  }

  private static void assertModelParticle(String modelPath, String expected) throws Exception {
    JsonObject root =
        JsonParser.parseString(Files.readString(Path.of(modelPath))).getAsJsonObject();

    assertEquals(expected, root.getAsJsonObject("textures").get("particle").getAsString());
  }

  private static void assertCroppedTexture(
      String sourcePath, String targetPath, int sourceX, int sourceY, int width, int height)
      throws Exception {
    BufferedImage source = ImageIO.read(Path.of(sourcePath).toFile());
    BufferedImage target = ImageIO.read(Path.of(targetPath).toFile());
    assertNotNull(source, sourcePath);
    assertNotNull(target, targetPath);
    assertEquals(width, target.getWidth(), targetPath);
    assertEquals(height, target.getHeight(), targetPath);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        assertEquals(
            source.getRGB(sourceX + x, sourceY + y),
            target.getRGB(x, y),
            targetPath + " pixel " + x + "," + y);
      }
    }
  }
}
