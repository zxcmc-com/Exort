package com.zxcmc.exort.infra.resourcepack.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BreakingModelGeneratorTest {
  @Test
  void generatedModelUsesRequestedOverlayTextureAndValidFaceUvs() {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            sourceModel(6, 6, 6, 10, 10, 10, "north", "east", "south", "west", "up", "down"),
            5,
            BreakingModelGenerator.identityTransform());

    assertFalse(elements(generated).isEmpty());
    assertAllFaceUvsInsideDestroySprite(generated);
    assertFaceTexturesResolve(generated);
    assertUsesOnlyOverlayStageTexture(generated, 5);
  }

  @Test
  void inwardFacesAreDroppedWithoutSplittingByTextureAlpha() {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            texturedSourceModel(6, 6, 0, 10, 10, 1, "north", "east", "south", "west", "up", "down"),
            0,
            BreakingModelGenerator.identityTransform(),
            Map.of("assets/exort/textures/test/alpha.png", new byte[] {0}));

    assertTrue(faces(generated).has("north"));
    assertFalse(faces(generated).has("south"));
    assertAllFaceUvsInsideDestroySprite(generated);
  }

  @Test
  void terminalPolicyKeepsDisplayScreenAndMasksLateFrontFrame() {
    JsonObject early =
        BreakingModelGenerator.generateTerminalModel(
            terminalSourceModel(), 5, BreakingModelGenerator.identityTransform());

    assertFalse(coversNorthFacePoint(early, 8, 8, 0));
    assertTrue(coversNorthFacePoint(early, 8, 8, 1));
    assertUsesOnlyOverlayStageTexture(early, 5);

    JsonObject late =
        BreakingModelGenerator.generateTerminalModel(
            terminalSourceModel(), 9, BreakingModelGenerator.identityTransform());

    assertTrue(modelUsesTexture(late, "exort:breaking/overlay/9"));
    assertTrue(modelUsesTexture(late, "exort:breaking/overlay/terminal"));
    assertAllFaceUvsInsideDestroySprite(late);
  }

  @Test
  void busPolicyDropsTransitionShelfButKeepsConnectorAndBodyOverlay() throws IOException {
    JsonObject generated =
        BreakingModelGenerator.generateBusModel(
            busSourceModel(),
            0,
            BreakingModelGenerator.identityTransform(),
            Map.of("assets/exort/textures/block/bus.png", pngBytes(blankTexture(32, 32))));

    assertFalse(hasElementBounds(generated, new double[] {1, 1, 2}, new double[] {15, 15, 4}));
    assertTrue(hasElementBounds(generated, new double[] {3, 3, -1}, new double[] {13, 13, 2}));
    assertTrue(hasElementBounds(generated, new double[] {0, 0, 4}, new double[] {16, 16, 16}));
    assertAllFaceUvsInsideDestroySprite(generated);
    assertFaceTexturesResolve(generated);
  }

  @Test
  void terminalAtlasPacksLastStagesWithTransparentCenter() throws IOException {
    Map<String, byte[]> entries = new HashMap<>();
    Map<Integer, BufferedImage> sources = new HashMap<>();
    for (int stage = 6; stage <= 9; stage++) {
      BufferedImage source = filledDestroyStageTexture(stage);
      sources.put(stage, source);
      entries.put("assets/exort/textures/breaking/overlay/" + stage + ".png", pngBytes(source));
    }

    BufferedImage atlas =
        ImageIO.read(
            new ByteArrayInputStream(BreakingModelGenerator.terminalBreakingAtlas(entries)));

    assertEquals(32, atlas.getWidth());
    assertEquals(32, atlas.getHeight());
    for (int stage = 6; stage <= 9; stage++) {
      int tileX = ((stage - 6) % 2) * 16;
      int tileY = ((stage - 6) / 2) * 16;
      assertEquals(0, atlas.getRGB(tileX + 8, tileY + 8));
      assertEquals(sources.get(stage).getRGB(0, 0), atlas.getRGB(tileX, tileY));
    }
  }

  @Test
  void nonZeroElementRotationsFailClearly() {
    JsonObject source = sourceModel(0, 0, 0, 16, 16, 16, "north");
    JsonObject rotation = new JsonObject();
    rotation.addProperty("angle", 22.5);
    rotation.addProperty("axis", "y");
    element(source).add("rotation", rotation);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            BreakingModelGenerator.generateModel(
                source, 0, BreakingModelGenerator.identityTransform()));
  }

  private static JsonObject sourceModel(
      double x1, double y1, double z1, double x2, double y2, double z2, String... faceNames) {
    JsonObject root = new JsonObject();
    JsonArray elements = new JsonArray();
    JsonObject element = new JsonObject();
    element.add("from", array(x1, y1, z1));
    element.add("to", array(x2, y2, z2));
    JsonObject faces = new JsonObject();
    for (String faceName : faceNames) {
      faces.add(faceName, new JsonObject());
    }
    element.add("faces", faces);
    elements.add(element);
    root.add("elements", elements);
    return root;
  }

  private static JsonObject texturedSourceModel(
      double x1, double y1, double z1, double x2, double y2, double z2, String... faceNames) {
    JsonObject root = new JsonObject();
    JsonObject textures = new JsonObject();
    textures.addProperty("0", "exort:test/alpha");
    root.add("textures", textures);

    JsonArray elements = new JsonArray();
    JsonObject element = new JsonObject();
    element.add("from", array(x1, y1, z1));
    element.add("to", array(x2, y2, z2));
    JsonObject faces = new JsonObject();
    for (String faceName : faceNames) {
      JsonObject face = new JsonObject();
      face.add("uv", array(0, 0, 16, 16));
      face.addProperty("texture", "#0");
      faces.add(faceName, face);
    }
    element.add("faces", faces);
    elements.add(element);
    root.add("elements", elements);
    return root;
  }

  private static JsonObject terminalSourceModel() {
    JsonObject root = new JsonObject();
    JsonObject textures = new JsonObject();
    textures.addProperty("display", "exort:display/inventory");
    textures.addProperty("body", "exort:block/terminal");
    root.add("textures", textures);

    JsonArray elements = new JsonArray();
    elements.add(
        element(
            new double[] {0, 0, 0},
            new double[] {16, 16, 16},
            Map.of("north", "#body", "east", "#body")));
    elements.add(
        element(new double[] {3, 3, 1}, new double[] {13, 13, 2}, Map.of("north", "#display")));
    elements.add(
        element(
            new double[] {3, 13, 0},
            new double[] {13, 14, 2},
            Map.of("north", "#body", "down", "#body")));
    root.add("elements", elements);
    return root;
  }

  private static JsonObject busSourceModel() {
    JsonObject root = new JsonObject();
    JsonObject textures = new JsonObject();
    textures.addProperty("0", "exort:block/bus");
    root.add("textures", textures);

    JsonArray elements = new JsonArray();
    elements.add(element(new double[] {1, 1, 2}, new double[] {15, 15, 4}, Map.of("east", "#0")));
    elements.add(
        element(
            new double[] {3, 3, -1},
            new double[] {13, 13, 2},
            Map.of("north", "#0", "east", "#0", "up", "#0")));
    elements.add(
        element(
            new double[] {0, 0, 4}, new double[] {16, 16, 16}, Map.of("east", "#0", "up", "#0")));
    root.add("elements", elements);
    return root;
  }

  private static JsonObject element(double[] from, double[] to, Map<String, String> faceTextures) {
    JsonObject element = new JsonObject();
    element.add("from", array(from[0], from[1], from[2]));
    element.add("to", array(to[0], to[1], to[2]));
    JsonObject faces = new JsonObject();
    for (Map.Entry<String, String> entry : faceTextures.entrySet()) {
      JsonObject face = new JsonObject();
      face.add("uv", array(0, 0, 16, 16));
      face.addProperty("texture", entry.getValue());
      faces.add(entry.getKey(), face);
    }
    element.add("faces", faces);
    return element;
  }

  private static JsonObject element(JsonObject model) {
    return model.getAsJsonArray("elements").get(0).getAsJsonObject();
  }

  private static JsonArray elements(JsonObject model) {
    return model.getAsJsonArray("elements");
  }

  private static JsonObject faces(JsonObject model) {
    return element(model).getAsJsonObject("faces");
  }

  private static JsonArray array(double... values) {
    JsonArray array = new JsonArray();
    for (double value : values) {
      array.add(value);
    }
    return array;
  }

  private static boolean coversNorthFacePoint(JsonObject model, double x, double y, double z) {
    for (JsonElement element : elements(model)) {
      JsonObject object = element.getAsJsonObject();
      JsonObject faces = object.getAsJsonObject("faces");
      if (faces == null || !faces.has("north")) {
        continue;
      }
      double[] from = doubleArray(object.getAsJsonArray("from"));
      double[] to = doubleArray(object.getAsJsonArray("to"));
      if (Math.abs(from[2] - z) < 0.000001
          && x >= from[0]
          && x <= to[0]
          && y >= from[1]
          && y <= to[1]) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasElementBounds(
      JsonObject model, double[] expectedFrom, double[] expectedTo) {
    for (JsonElement element : elements(model)) {
      JsonObject object = element.getAsJsonObject();
      if (arraysEqual(expectedFrom, doubleArray(object.getAsJsonArray("from")))
          && arraysEqual(expectedTo, doubleArray(object.getAsJsonArray("to")))) {
        return true;
      }
    }
    return false;
  }

  private static boolean modelUsesTexture(JsonObject model, String expected) {
    JsonObject textures = model.getAsJsonObject("textures");
    for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
      if (expected.equals(entry.getValue().getAsString())) {
        return true;
      }
    }
    return false;
  }

  private static void assertAllFaceUvsInsideDestroySprite(JsonObject model) {
    for (JsonElement element : elements(model)) {
      JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
      if (faces == null) {
        continue;
      }
      for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
        double[] uv = doubleArray(entry.getValue().getAsJsonObject().getAsJsonArray("uv"));
        for (double value : uv) {
          assertTrue(
              value >= 0.0 && value <= 16.0,
              () -> entry.getKey() + " face has out-of-sprite UV " + value);
        }
      }
    }
  }

  private static void assertFaceTexturesResolve(JsonObject model) {
    JsonObject textures = model.getAsJsonObject("textures");
    for (JsonElement element : elements(model)) {
      JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
      if (faces == null) {
        continue;
      }
      for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
        String texture = entry.getValue().getAsJsonObject().get("texture").getAsString();
        assertTrue(texture.startsWith("#"), texture);
        assertTrue(textures.has(texture.substring(1)), texture);
      }
    }
  }

  private static void assertUsesOnlyOverlayStageTexture(JsonObject model, int stage) {
    JsonObject textures = model.getAsJsonObject("textures");
    assertEquals("exort:breaking/overlay/" + stage, textures.get("0").getAsString());
    assertEquals("exort:breaking/overlay/" + stage, textures.get("particle").getAsString());
    assertFaceTexturesResolve(model);
  }

  private static double[] doubleArray(JsonArray array) {
    double[] result = new double[array.size()];
    for (int i = 0; i < array.size(); i++) {
      result[i] = array.get(i).getAsDouble();
    }
    return result;
  }

  private static boolean arraysEqual(double[] expected, double[] actual) {
    if (expected.length != actual.length) {
      return false;
    }
    for (int i = 0; i < expected.length; i++) {
      if (Math.abs(expected[i] - actual[i]) > 0.000001) {
        return false;
      }
    }
    return true;
  }

  private static BufferedImage filledDestroyStageTexture(int stage) {
    BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 16; y++) {
      for (int x = 0; x < 16; x++) {
        int alpha = 0x40 + stage;
        int red = stage * 10;
        int green = x * 8;
        int blue = y * 8;
        image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
      }
    }
    return image;
  }

  private static BufferedImage blankTexture(int width, int height) {
    return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
  }

  private static byte[] pngBytes(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(image, "png", out));
    return out.toByteArray();
  }
}
