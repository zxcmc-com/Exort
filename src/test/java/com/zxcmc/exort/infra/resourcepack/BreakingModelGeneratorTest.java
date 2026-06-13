package com.zxcmc.exort.infra.resourcepack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zxcmc.exort.display.DisplayRotation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import org.bukkit.block.BlockFace;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

class BreakingModelGeneratorTest {
  @Test
  void densityTextureSizeCapsDetailedFacesAtTwoTimesDestroyDensity() {
    assertEquals(
        new BreakingModelGenerator.TextureSize(32, 32),
        BreakingModelGenerator.densityTextureSize(
            "north",
            new double[] {3, 3, 1},
            new double[] {13, 13, 2},
            64,
            64,
            new double[] {0, 0, 5, 5}));
    assertEquals(
        new BreakingModelGenerator.TextureSize(32, 32),
        BreakingModelGenerator.densityTextureSize(
            "north",
            new double[] {0, 0, 0},
            new double[] {16, 16, 16},
            16,
            16,
            new double[] {0, 0, 16, 16}));
    assertEquals(
        new BreakingModelGenerator.TextureSize(32, 16),
        BreakingModelGenerator.densityTextureSize(
            "north",
            new double[] {13, 2, 0},
            new double[] {14, 14, 2},
            32,
            16,
            new double[] {0, 0, 0.5, 0.5}));
  }

  @Test
  void fullCubeKeepsPreviousFullFaceUvPattern() {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            sourceModel(0, 0, 0, 16, 16, 16, "north", "east", "south", "west", "up", "down"),
            0,
            BreakingModelGenerator.identityTransform());

    assertArrayEquals(new double[] {0, 0, 16, 16}, uv(generated, "north"));
    assertArrayEquals(new double[] {16, 0, 0, 16}, uv(generated, "east"));
    assertArrayEquals(new double[] {0, 0, 16, 16}, uv(generated, "south"));
    assertArrayEquals(new double[] {16, 0, 0, 16}, uv(generated, "west"));
    assertArrayEquals(new double[] {16, 16, 0, 0}, uv(generated, "up"));
    assertArrayEquals(new double[] {16, 16, 0, 0}, uv(generated, "down"));
  }

  @Test
  void smallCuboidsUseBlockSpaceUvs() {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            sourceModel(6, 6, 6, 10, 10, 10, "north", "east", "up"),
            0,
            BreakingModelGenerator.identityTransform());

    assertArrayEquals(new double[] {6, 6, 10, 10}, uv(generated, "north"));
    assertArrayEquals(new double[] {10, 6, 6, 10}, uv(generated, "east"));
    assertArrayEquals(new double[] {10, 10, 6, 6}, uv(generated, "up"));
  }

  @Test
  void opaqueAlphaKeepsSingleFullFace() throws IOException {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            texturedSourceModel(0, 0, 0, 16, 16, 16, "north", 0),
            0,
            BreakingModelGenerator.identityTransform(),
            textureEntries(alphaImage(2, 2, (x, y) -> 255)));

    assertEquals(1, elements(generated).size());
    assertArrayEquals(new double[] {0, 0, 16, 16}, uv(generated, "north"));
  }

  @Test
  void fullyTransparentAlphaSkipsFace() throws IOException {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            texturedSourceModel(0, 0, 0, 16, 16, 16, "north", 0),
            0,
            BreakingModelGenerator.identityTransform(),
            textureEntries(alphaImage(2, 2, (x, y) -> 0)));

    assertEquals(0, elements(generated).size());
  }

  @Test
  void mixedAlphaSplitsFrameAndDoesNotCoverTransparentCenter() throws IOException {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            texturedSourceModel(0, 0, 0, 16, 16, 16, "north", 0),
            0,
            BreakingModelGenerator.identityTransform(),
            textureEntries(
                alphaImage(4, 4, (x, y) -> x == 0 || x == 3 || y == 0 || y == 3 ? 255 : 0)));

    assertEquals(4, elements(generated).size());
    assertFalse(coversNorthFacePoint(generated, 8, 8, 0));
    assertTrue(coversNorthFacePoint(generated, 1, 15, 0));
    assertTrue(coversNorthFacePoint(generated, 1, 8, 0));
    assertTrue(coversNorthFacePoint(generated, 15, 8, 0));
    assertTrue(coversNorthFacePoint(generated, 8, 1, 0));
  }

  @Test
  void nonZeroAlphaCountsAsVisible() throws IOException {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            texturedSourceModel(0, 0, 0, 16, 16, 16, "north", 0),
            0,
            BreakingModelGenerator.identityTransform(),
            textureEntries(alphaImage(1, 1, (x, y) -> 1)));

    assertEquals(1, elements(generated).size());
  }

  @Test
  void splitFacesKeepBlockSpaceDestroyUvs() throws IOException {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            texturedSourceModel(4, 4, 0, 12, 12, 2, "north", 0),
            0,
            BreakingModelGenerator.identityTransform(),
            textureEntries(alphaImage(2, 1, (x, y) -> x == 0 ? 255 : 0)));

    assertEquals(1, elements(generated).size());
    assertArrayEquals(new double[] {4, 4, 0}, vector(generated, "from"));
    assertArrayEquals(new double[] {8, 12, 2}, vector(generated, "to"));
    assertArrayEquals(new double[] {4, 4, 8, 12}, uv(generated, "north"));
  }

  @Test
  void overhangingGeometryKeepsFullBoundsAndFitsProjectedDestroyUvsWithoutScaleLoss() {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            sourceModel(3, 3, -1, 13, 13, 2, "north", "south", "east", "west", "up", "down"),
            0,
            BreakingModelGenerator.identityTransform());

    assertEquals(1, elements(generated).size());
    assertAllFaceUvsInsideDestroySprite(generated);
    assertTrue(
        hasFaceBoundsAndUv(
            generated,
            "north",
            new double[] {3, 3, -1},
            new double[] {13, 13, 2},
            new double[] {3, 3, 13, 13}));
    assertTrue(
        hasFaceBoundsAndUv(
            generated,
            "east",
            new double[] {3, 3, -1},
            new double[] {13, 13, 2},
            new double[] {16, 3, 13, 13}));
    assertTrue(
        hasFaceBoundsAndUv(
            generated,
            "up",
            new double[] {3, 3, -1},
            new double[] {13, 13, 2},
            new double[] {13, 16, 3, 13}));
  }

  @Test
  void displayRotationsRemapBoundsAndFaces() {
    JsonObject source = sourceModel(0, 0, 0, 2, 4, 6, "north", "east", "up");

    JsonObject storageNorth =
        BreakingModelGenerator.generateModel(
            source,
            0,
            new BreakingModelGenerator.Transform(
                DisplayRotation.rotationForFacing(BlockFace.NORTH)));
    assertArrayEquals(new double[] {14, 0, 10}, vector(storageNorth, "from"));
    assertArrayEquals(new double[] {16, 4, 16}, vector(storageNorth, "to"));
    assertTrue(faces(storageNorth).has("south"));
    assertTrue(faces(storageNorth).has("west"));
    assertTrue(faces(storageNorth).has("up"));

    JsonObject busUp =
        BreakingModelGenerator.generateModel(
            source,
            0,
            new BreakingModelGenerator.Transform(
                DisplayRotation.rotationForFacingFull(BlockFace.UP)));
    assertTrue(faces(busUp).has("down"));
    assertTrue(faces(busUp).has("east"));
    assertTrue(faces(busUp).has("north"));

    JsonObject wireMirror =
        BreakingModelGenerator.generateModel(
            source,
            0,
            new BreakingModelGenerator.Transform(new Quaternionf().rotateY((float) Math.PI)));
    assertTrue(faces(wireMirror).has("south"));
    assertTrue(faces(wireMirror).has("west"));
    assertTrue(faces(wireMirror).has("up"));
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

  @Test
  void unsupportedFaceTextureRotationsFailClearly() throws IOException {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BreakingModelGenerator.generateModel(
                texturedSourceModel(0, 0, 0, 16, 16, 16, "north", 45),
                0,
                BreakingModelGenerator.identityTransform(),
                textureEntries(alphaImage(1, 1, (x, y) -> 255))));
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
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      String faceName,
      int rotation) {
    JsonObject root = new JsonObject();
    JsonObject textures = new JsonObject();
    textures.addProperty("0", "exort:test/alpha");
    root.add("textures", textures);

    JsonArray elements = new JsonArray();
    JsonObject element = new JsonObject();
    element.add("from", array(x1, y1, z1));
    element.add("to", array(x2, y2, z2));
    JsonObject face = new JsonObject();
    face.add("uv", array(0, 0, 16, 16));
    face.addProperty("texture", "#0");
    if (rotation != 0) {
      face.addProperty("rotation", rotation);
    }
    JsonObject faces = new JsonObject();
    faces.add(faceName, face);
    element.add("faces", faces);
    elements.add(element);
    root.add("elements", elements);
    return root;
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

  private static double[] uv(JsonObject model, String face) {
    return doubleArray(faces(model).getAsJsonObject(face).getAsJsonArray("uv"));
  }

  private static double[] vector(JsonObject model, String key) {
    return doubleArray(element(model).getAsJsonArray(key));
  }

  private static double[] doubleArray(JsonArray array) {
    double[] result = new double[array.size()];
    for (int i = 0; i < array.size(); i++) {
      result[i] = array.get(i).getAsDouble();
    }
    return result;
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

  private static boolean hasFaceBoundsAndUv(
      JsonObject model,
      String face,
      double[] expectedFrom,
      double[] expectedTo,
      double[] expectedUv) {
    for (JsonElement element : elements(model)) {
      JsonObject object = element.getAsJsonObject();
      JsonObject faces = object.getAsJsonObject("faces");
      if (faces == null || !faces.has(face)) {
        continue;
      }
      if (arraysEqual(expectedFrom, doubleArray(object.getAsJsonArray("from")))
          && arraysEqual(expectedTo, doubleArray(object.getAsJsonArray("to")))
          && arraysEqual(
              expectedUv, doubleArray(faces.getAsJsonObject(face).getAsJsonArray("uv")))) {
        return true;
      }
    }
    return false;
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

  private static Map<String, byte[]> textureEntries(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return Map.of("assets/exort/textures/test/alpha.png", out.toByteArray());
  }

  private static BufferedImage alphaImage(int width, int height, AlphaSource alphaSource) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int alpha = alphaSource.alpha(x, y) & 0xFF;
        image.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
      }
    }
    return image;
  }

  private interface AlphaSource {
    int alpha(int x, int y);
  }
}
