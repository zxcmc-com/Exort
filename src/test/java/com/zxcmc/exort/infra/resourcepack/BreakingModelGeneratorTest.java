package com.zxcmc.exort.infra.resourcepack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zxcmc.exort.display.DisplayRotation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.bukkit.block.BlockFace;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

class BreakingModelGeneratorTest {
  @Test
  void fullCubeKeepsProjectedDestroyUvPattern() {
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
    assertUsesOnlyOverlayStageTexture(generated, 0);
  }

  @Test
  void centeredCuboidsKeepAllOuterFaces() {
    JsonObject generated =
        BreakingModelGenerator.generateModel(
            sourceModel(6, 6, 6, 10, 10, 10, "north", "east", "south", "west", "up", "down"),
            5,
            BreakingModelGenerator.identityTransform());

    assertEquals(1, elements(generated).size());
    assertEquals(6, faces(generated).size());
    assertArrayEquals(new double[] {6, 6, 10, 10}, uv(generated, "north"));
    assertArrayEquals(new double[] {10, 6, 6, 10}, uv(generated, "east"));
    assertArrayEquals(new double[] {10, 10, 6, 6}, uv(generated, "up"));
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

    assertEquals(1, elements(generated).size());
    assertTrue(faces(generated).has("north"));
    assertTrue(faces(generated).has("east"));
    assertTrue(faces(generated).has("west"));
    assertTrue(faces(generated).has("up"));
    assertTrue(faces(generated).has("down"));
    assertFalse(faces(generated).has("south"));
  }

  @Test
  void overhangingGeometryKeepsBoundsAndFitsProjectedDestroyUvs() {
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
  void busPolicyAlignsProtrusionAndCentersBodyDestroyUvs() throws IOException {
    JsonObject generated =
        BreakingModelGenerator.generateBusModel(
            busSourceModel(),
            0,
            BreakingModelGenerator.identityTransform(),
            Map.of("assets/exort/textures/block/bus.png", pngBytes(blankTexture(32, 32))));

    JsonObject protrusionNorth =
        faceWithBounds(generated, "north", new double[] {3, 3, -1}, new double[] {13, 13, 2});
    assertArrayEquals(
        new double[] {2, 2, 14, 14}, doubleArray(protrusionNorth.getAsJsonArray("uv")));
    assertFalse(
        hasFaceBounds(generated, "east", new double[] {3, 3, -1}, new double[] {13, 13, 2}));
    assertFalse(hasFaceBounds(generated, "up", new double[] {3, 3, -1}, new double[] {13, 13, 2}));
    assertFalse(hasFaceBounds(generated, "east", new double[] {1, 1, 2}, new double[] {15, 15, 4}));
    assertFalse(hasFaceBounds(generated, "up", new double[] {1, 1, 2}, new double[] {15, 15, 4}));

    JsonObject bodyEast =
        faceWithBounds(generated, "east", new double[] {0, 0, 4}, new double[] {16, 16, 16});
    assertArrayEquals(new double[] {14, 0, 2, 16}, doubleArray(bodyEast.getAsJsonArray("uv")));

    JsonObject bodyUp =
        faceWithBounds(generated, "up", new double[] {0, 0, 4}, new double[] {16, 16, 16});
    assertArrayEquals(new double[] {16, 14, 0, 2}, doubleArray(bodyUp.getAsJsonArray("uv")));
  }

  @Test
  void busPolicyCentersBodyUvWhenTextureCannotBeResolved() {
    JsonObject generated =
        BreakingModelGenerator.generateBusModel(
            sourceModel(0, 0, 4, 16, 16, 16, "east", "up"),
            0,
            BreakingModelGenerator.identityTransform(),
            Map.of());

    assertArrayEquals(new double[] {14, 0, 2, 16}, uv(generated, "east"));
    assertArrayEquals(new double[] {16, 14, 0, 2}, uv(generated, "up"));
  }

  @Test
  void displayRotationsRemapBoundsAndKeepOnlyExternalFaces() {
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
    assertFalse(faces(storageNorth).has("west"));
    assertFalse(faces(storageNorth).has("up"));

    JsonObject busUp =
        BreakingModelGenerator.generateModel(
            source,
            0,
            new BreakingModelGenerator.Transform(
                DisplayRotation.rotationForFacingFull(BlockFace.UP)));
    assertTrue(faces(busUp).has("down"));
    assertFalse(faces(busUp).has("east"));
    assertFalse(faces(busUp).has("north"));
    assertEquals(1, faces(busUp).size());

    JsonObject wireMirror =
        BreakingModelGenerator.generateModel(
            source,
            0,
            new BreakingModelGenerator.Transform(new Quaternionf().rotateY((float) Math.PI)));
    assertTrue(faces(wireMirror).has("south"));
    assertFalse(faces(wireMirror).has("west"));
    assertFalse(faces(wireMirror).has("up"));
  }

  @Test
  void storageModelGeneratesOneCheapOverlayElementPerSourceCuboid() throws IOException {
    JsonObject source =
        JsonParser.parseString(
                Files.readString(
                    Path.of("src/main/resources/pack/assets/exort/models/storage/storage.json")))
            .getAsJsonObject();

    JsonObject generated =
        BreakingModelGenerator.generateModel(source, 9, BreakingModelGenerator.identityTransform());

    assertEquals(17, elements(generated).size());
    assertEquals(45, faceCount(generated));
    assertAllFaceUvsInsideDestroySprite(generated);
    assertUsesOnlyOverlayStageTexture(generated, 9);
  }

  @Test
  void terminalPolicyDropsOpaqueFrontFacesButKeepsDisplayScreen() {
    JsonObject generated =
        BreakingModelGenerator.generateTerminalModel(
            terminalSourceModel(), 5, BreakingModelGenerator.identityTransform());

    assertFalse(coversNorthFacePoint(generated, 8, 8, 0));
    assertTrue(coversNorthFacePoint(generated, 8, 8, 1));
    assertTrue(hasFaceBounds(generated, "east", new double[] {0, 0, 0}, new double[] {16, 16, 16}));
    assertUsesOnlyOverlayStageTexture(generated, 5);
  }

  @Test
  void terminalPolicyAddsMaskedFrontFrameAtlasOnlyForLateStages() {
    JsonObject generated =
        BreakingModelGenerator.generateTerminalModel(
            terminalSourceModel(), 7, BreakingModelGenerator.identityTransform());

    JsonObject textures = generated.getAsJsonObject("textures");
    assertEquals("exort:breaking/overlay/7", textures.get("0").getAsString());
    assertEquals("exort:breaking/overlay/terminal", textures.get("1").getAsString());
    assertEquals("exort:breaking/overlay/7", textures.get("particle").getAsString());
    assertEquals(3, textures.size());

    JsonObject screenFace =
        faceWithBounds(generated, "north", new double[] {3, 3, 1}, new double[] {13, 13, 2});
    assertEquals("#0", screenFace.get("texture").getAsString());
    assertArrayEquals(new double[] {3, 3, 13, 13}, doubleArray(screenFace.getAsJsonArray("uv")));

    JsonObject frameFace =
        faceWithBounds(generated, "north", new double[] {0, 0, 0}, new double[] {16, 16, 16});
    assertEquals("#1", frameFace.get("texture").getAsString());
    assertArrayEquals(new double[] {8, 0, 16, 8}, doubleArray(frameFace.getAsJsonArray("uv")));
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
      BufferedImage source = sources.get(stage);
      for (int y = 0; y < 16; y++) {
        for (int x = 0; x < 16; x++) {
          int expected = x >= 2 && x < 14 && y >= 2 && y < 14 ? 0 : source.getRGB(x, y);
          assertEquals(expected, atlas.getRGB(tileX + x, tileY + y));
        }
      }
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
        terminalElement(
            new double[] {0, 0, 0},
            new double[] {16, 16, 16},
            Map.of("north", "#body", "east", "#body")));
    elements.add(
        terminalElement(
            new double[] {3, 3, 1}, new double[] {13, 13, 2}, Map.of("north", "#display")));
    elements.add(
        terminalElement(
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
    JsonObject transition = new JsonObject();
    transition.add("from", array(1, 1, 2));
    transition.add("to", array(15, 15, 4));
    JsonObject transitionFaces = new JsonObject();
    transitionFaces.add("east", face(7.5, 0, 8.5, 8, 0));
    transitionFaces.add("up", face(7.5, 0, 8.5, 8, 270));
    transition.add("faces", transitionFaces);
    elements.add(transition);

    JsonObject protrusion = new JsonObject();
    protrusion.add("from", array(3, 3, -1));
    protrusion.add("to", array(13, 13, 2));
    JsonObject protrusionFaces = new JsonObject();
    protrusionFaces.add("north", face(9, 1, 15, 7, 0));
    protrusionFaces.add("east", face(13.5, 2, 15, 6, 0));
    protrusionFaces.add("up", face(10, 1, 14, 2.5, 0));
    protrusion.add("faces", protrusionFaces);
    elements.add(protrusion);

    JsonObject body = new JsonObject();
    body.add("from", array(0, 0, 4));
    body.add("to", array(16, 16, 16));
    JsonObject bodyFaces = new JsonObject();
    bodyFaces.add("east", face(6, 16, 0, 8, 0));
    bodyFaces.add("up", face(6, 8, 0, 16, 270));
    body.add("faces", bodyFaces);
    elements.add(body);

    root.add("elements", elements);
    return root;
  }

  private static JsonObject face(double u1, double v1, double u2, double v2, int rotation) {
    JsonObject face = new JsonObject();
    face.add("uv", array(u1, v1, u2, v2));
    face.addProperty("texture", "#0");
    if (rotation != 0) {
      face.addProperty("rotation", rotation);
    }
    return face;
  }

  private static JsonObject terminalElement(
      double[] from, double[] to, Map<String, String> faceTextures) {
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

  private static int faceCount(JsonObject model) {
    int count = 0;
    for (JsonElement element : elements(model)) {
      JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
      if (faces != null) {
        count += faces.size();
      }
    }
    return count;
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

  private static void assertUsesOnlyOverlayStageTexture(JsonObject model, int stage) {
    JsonObject textures = model.getAsJsonObject("textures");
    assertEquals("exort:breaking/overlay/" + stage, textures.get("0").getAsString());
    assertEquals("exort:breaking/overlay/" + stage, textures.get("particle").getAsString());
    assertEquals(2, textures.size());
    for (JsonElement element : elements(model)) {
      JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
      if (faces == null) {
        continue;
      }
      for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
        assertEquals("#0", entry.getValue().getAsJsonObject().get("texture").getAsString());
        assertEquals(0, entry.getValue().getAsJsonObject().get("tintindex").getAsInt());
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

  private static JsonObject faceWithBounds(
      JsonObject model, String face, double[] expectedFrom, double[] expectedTo) {
    for (JsonElement element : elements(model)) {
      JsonObject object = element.getAsJsonObject();
      JsonObject faces = object.getAsJsonObject("faces");
      if (faces == null || !faces.has(face)) {
        continue;
      }
      if (arraysEqual(expectedFrom, doubleArray(object.getAsJsonArray("from")))
          && arraysEqual(expectedTo, doubleArray(object.getAsJsonArray("to")))) {
        return faces.getAsJsonObject(face);
      }
    }
    throw new AssertionError("missing " + face + " face for expected bounds");
  }

  private static boolean hasFaceBounds(
      JsonObject model, String face, double[] expectedFrom, double[] expectedTo) {
    for (JsonElement element : elements(model)) {
      JsonObject object = element.getAsJsonObject();
      JsonObject faces = object.getAsJsonObject("faces");
      if (faces == null || !faces.has(face)) {
        continue;
      }
      if (arraysEqual(expectedFrom, doubleArray(object.getAsJsonArray("from")))
          && arraysEqual(expectedTo, doubleArray(object.getAsJsonArray("to")))) {
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
