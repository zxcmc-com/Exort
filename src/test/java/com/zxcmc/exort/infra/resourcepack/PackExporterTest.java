package com.zxcmc.exort.infra.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackExporterTest {
  @TempDir Path tempDir;

  @Test
  void bundledRuntimeLanguageFilesAreConvertedDuringExport() throws IOException {
    Path source = tempDir.resolve("source");
    Path langDir = source.resolve("lang");
    Files.createDirectories(langDir);
    Files.writeString(langDir.resolve("index.yml"), "languages:\n- en_us\n- rpr\n");
    Files.writeString(
        langDir.resolve("en_us.yml"),
        "message:\n  no_permission: No permission.\nitem:\n  terminal: Storage Terminal\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        langDir.resolve("rpr.yml"),
        "message:\n  no_permission: Нѣтъ правъ.\nitem:\n  terminal: Терминалъ хранилища\n",
        StandardCharsets.UTF_8);

    PackExporter.Result result =
        PackExporter.exportPack(
            source, "pack/", tempDir.resolve("out").toFile(), Logger.getLogger("test"));

    assertTrue(result.available());
    assertEquals(
        "Storage Terminal",
        langValue(
            result.outputFile().toPath(), "assets/exort/lang/en_us.json", "exort.item.terminal"));
    assertEquals(
        "Терминалъ хранилища",
        langValue(
            result.outputFile().toPath(), "assets/exort/lang/rpr.json", "exort.item.terminal"));
    assertFalse(
        hasKey(
            result.outputFile().toPath(),
            "assets/exort/lang/en_us.json",
            "exort.message.no_permission"));
  }

  @Test
  void projectPackExportIncludesEveryPinnedLanguageFile() throws IOException {
    PackExporter.Result result =
        PackExporter.exportPack(
            Path.of("src/main/resources"),
            "pack/",
            tempDir.resolve("project-out").toFile(),
            Logger.getLogger("test"));

    assertTrue(result.available());
    try (ZipFile zip = new ZipFile(result.outputFile())) {
      for (String locale : pinnedLocales()) {
        assertTrue(
            zip.getEntry("assets/exort/lang/" + locale + ".json") != null,
            "missing exported Exort language file for " + locale);
      }
    }
  }

  @Test
  void projectPackExportIncludesGeneratedBreakingOverlayModels() throws IOException {
    PackExporter.Result result =
        PackExporter.exportPack(
            Path.of("src/main/resources"),
            "pack/",
            tempDir.resolve("breaking-out").toFile(),
            Logger.getLogger("test"));

    assertTrue(result.available());
    try (ZipFile zip = new ZipFile(result.outputFile())) {
      assertEntry(zip, "assets/exort/items/breaking/storage/core/stage_0.json");
      assertEntry(zip, "assets/exort/models/breaking/storage/south/stage_0.json");
      assertEntry(zip, "assets/exort/items/breaking/terminal/north/stage_3.json");
      assertEntry(zip, "assets/exort/models/breaking/bus/up/stage_9.json");
      assertEntry(zip, "assets/exort/items/breaking/wire/center/stage_0.json");
      assertEntry(zip, "assets/exort/models/breaking/wire/dnsew/stage_5.json");
      assertEntry(zip, "assets/exort/textures/breaking/block.png");
      assertEntry(zip, "assets/exort/textures/breaking/storage.png");
      assertEntry(zip, "assets/exort/textures/breaking/wire.png");
      assertAllFaceUvsInsideDestroySprite(
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/bus/south/stage_0.json"))
              .getAsJsonObject());
      assertAllFaceUvsInsideDestroySprite(
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/bus/north/stage_0.json"))
              .getAsJsonObject());
      var busUpBreakingModel =
          JsonParser.parseString(readEntry(zip, "assets/exort/models/breaking/bus/up/stage_0.json"))
              .getAsJsonObject();
      var busDownBreakingModel =
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/bus/down/stage_0.json"))
              .getAsJsonObject();
      assertAllFaceUvsInsideDestroySprite(busUpBreakingModel);
      assertAllFaceUvsInsideDestroySprite(busDownBreakingModel);
      assertTrue(
          hasElementBounds(busUpBreakingModel, new double[] {3, 14, 3}, new double[] {13, 17, 13}),
          "bus/up breaking overlay must keep the connector overhang on the top side");
      JsonObject busUpConnectorSide =
          faceWithBounds(
              busUpBreakingModel, "south", new double[] {3, 14, 3}, new double[] {13, 17, 13});
      JsonObject busUpConnectorTop =
          faceWithBounds(
              busUpBreakingModel, "up", new double[] {3, 14, 3}, new double[] {13, 17, 13});
      assertTrue(
          arraysEqual(
              new double[] {13, 13, 3, 3}, doubleArray(busUpConnectorTop.getAsJsonArray("uv"))),
          "bus/up large connector face must keep projected UV to avoid compressed cracks");
      assertEquals(
          "exort:breaking/generated/destroy_stage_0_32x32",
          texturePath(busUpBreakingModel.getAsJsonObject("textures"), busUpConnectorTop),
          "bus/up large connector face must keep normal projected density");
      assertTrue(
          arraysEqual(
              new double[] {3, 0, 13, 3}, doubleArray(busUpConnectorSide.getAsJsonArray("uv"))),
          "bus/up connector side overlay must keep projected UVs instead of stretching source UVs");
      assertEquals(
          "exort:breaking/generated/destroy_stage_0_32x32",
          texturePath(busUpBreakingModel.getAsJsonObject("textures"), busUpConnectorSide),
          "bus breaking overlay must keep normal projected density without 64x64 texture bloat");
      assertTrue(
          hasElementBounds(busDownBreakingModel, new double[] {3, -1, 3}, new double[] {13, 2, 13}),
          "bus/down breaking overlay must keep the connector overhang on the bottom side");
      for (int mask = 1; mask < 64; mask++) {
        String key = wireSuffix(mask);
        assertEntry(zip, "assets/exort/items/breaking/wire/" + key + "/stage_0.json");
        assertEntry(zip, "assets/exort/models/breaking/wire/" + key + "/stage_0.json");
      }
      assertFalse(zip.getEntry("assets/exort/items/breaking/stage_0.json") != null);
      assertFalse(zip.getEntry("assets/exort/models/breaking/stage_0.json") != null);

      String atlas = readEntry(zip, "assets/minecraft/atlases/blocks.json");
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/block\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/storage\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/wire\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/destroy_stage_0\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/destroy_stage_9\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/generated/destroy_stage_0_32x32\""));
      assertTrue(
          atlas.contains("\"resource\":\"exort:breaking/generated/destroy_stage_0_32x32_a220\""));
      assertFalse(
          atlas.contains("\"resource\":\"exort:breaking/generated/destroy_stage_0_64x64\""));

      var terminalBreakingModel =
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/terminal/south/stage_0.json"))
              .getAsJsonObject();
      assertAllFaceUvsInsideDestroySprite(terminalBreakingModel);
      assertFalse(
          coversNorthFacePoint(terminalBreakingModel, 8, 8, 0),
          "terminal base transparent screen cutout must not receive cracks");
      assertTrue(
          coversNorthFacePoint(terminalBreakingModel, 8, 8, 1),
          "terminal screen display face must still receive cracks");
      JsonObject terminalTextures = terminalBreakingModel.getAsJsonObject("textures");
      JsonObject screenFace =
          faceWithBounds(
              terminalBreakingModel, "north", new double[] {3, 3, 1}, new double[] {13, 13, 2});
      assertEquals(
          "exort:breaking/generated/destroy_stage_0_32x32_a220",
          texturePath(terminalTextures, screenFace),
          "terminal display face must use the less transparent screen crack texture");
      assertTrue(
          arraysEqual(new double[] {3, 3, 13, 13}, doubleArray(screenFace.getAsJsonArray("uv"))),
          "terminal display face must keep world-aligned projected UVs");

      JsonObject bodyFace = firstFace(terminalBreakingModel, "south");
      assertEquals(
          "exort:breaking/generated/destroy_stage_0_32x32",
          texturePath(terminalTextures, bodyFace),
          "normal 16-pixel terminal body faces must use 2x density instead of screen density");
      assertImageSize(
          zip, "assets/exort/textures/breaking/generated/destroy_stage_0_32x32.png", 32, 32);
      assertImageAlphaCount(
          zip, "assets/exort/textures/breaking/generated/destroy_stage_0_32x32.png", 180, 16);
      assertImageColorCount(
          zip, "assets/exort/textures/breaking/generated/destroy_stage_0_32x32.png", 0xB43D3D3D, 8);
      assertImageColorCount(
          zip, "assets/exort/textures/breaking/generated/destroy_stage_0_32x32.png", 0xB49B9B9B, 8);
      assertImageSize(
          zip, "assets/exort/textures/breaking/generated/destroy_stage_0_32x32_a220.png", 32, 32);
      assertImageAlphaCount(
          zip, "assets/exort/textures/breaking/generated/destroy_stage_0_32x32_a220.png", 220, 16);
      assertImageAlphaCount(
          zip, "assets/exort/textures/breaking/generated/destroy_stage_0_32x32_a220.png", 180, 0);
      assertImageColorCount(
          zip,
          "assets/exort/textures/breaking/generated/destroy_stage_0_32x32_a220.png",
          0xDC3D3D3D,
          8);
      assertImageColorCount(
          zip,
          "assets/exort/textures/breaking/generated/destroy_stage_0_32x32_a220.png",
          0xDC9B9B9B,
          8);
      assertTrue(
          zip.getEntry("assets/exort/textures/breaking/generated/destroy_stage_0_64x64.png")
              == null,
          "breaking overlay export must not generate 64x64 density textures for current models");
      assertGeneratedBreakingTexturesAreNotDownscaled(zip);
    }
  }

  private static String langValue(Path pack, String entryName, String key) throws IOException {
    try (ZipFile zip = new ZipFile(pack.toFile())) {
      var entry = zip.getEntry(entryName);
      String json = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
      return JsonParser.parseString(json).getAsJsonObject().get(key).getAsString();
    }
  }

  private static boolean hasKey(Path pack, String entryName, String key) throws IOException {
    try (ZipFile zip = new ZipFile(pack.toFile())) {
      var entry = zip.getEntry(entryName);
      String json = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
      return JsonParser.parseString(json).getAsJsonObject().has(key);
    }
  }

  private static void assertEntry(ZipFile zip, String name) {
    assertTrue(zip.getEntry(name) != null, "missing pack entry " + name);
  }

  private static void assertImageSize(ZipFile zip, String name, int width, int height)
      throws IOException {
    var entry = zip.getEntry(name);
    assertTrue(entry != null, "missing pack image " + name);
    BufferedImage image = ImageIO.read(zip.getInputStream(entry));
    assertEquals(width, image.getWidth(), name + " width");
    assertEquals(height, image.getHeight(), name + " height");
  }

  private static void assertImageAlphaCount(ZipFile zip, String name, int alpha, int count)
      throws IOException {
    var entry = zip.getEntry(name);
    assertTrue(entry != null, "missing pack image " + name);
    BufferedImage image = ImageIO.read(zip.getInputStream(entry));
    int actual = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (((image.getRGB(x, y) >>> 24) & 0xFF) == alpha) {
          actual++;
        }
      }
    }
    assertEquals(count, actual, name + " alpha " + alpha + " pixel count");
  }

  private static void assertImageColorCount(ZipFile zip, String name, int argb, int count)
      throws IOException {
    var entry = zip.getEntry(name);
    assertTrue(entry != null, "missing pack image " + name);
    BufferedImage image = ImageIO.read(zip.getInputStream(entry));
    int actual = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (image.getRGB(x, y) == argb) {
          actual++;
        }
      }
    }
    assertEquals(count, actual, name + " color 0x" + Integer.toHexString(argb) + " count");
  }

  private static void assertGeneratedBreakingTexturesAreNotDownscaled(ZipFile zip) {
    Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
      String name = entries.nextElement().getName();
      if (!name.startsWith("assets/exort/textures/breaking/generated/destroy_stage_")
          || !name.endsWith(".png")) {
        continue;
      }
      String stem = name.substring(name.lastIndexOf('/') + 1, name.length() - ".png".length());
      int sizeSeparator = stem.lastIndexOf('_');
      if (stem.substring(sizeSeparator + 1).startsWith("a")) {
        stem = stem.substring(0, sizeSeparator);
        sizeSeparator = stem.lastIndexOf('_');
      }
      String[] dimensions = stem.substring(sizeSeparator + 1).split("x", 2);
      int width = Integer.parseInt(dimensions[0]);
      int height = Integer.parseInt(dimensions[1]);
      assertTrue(width >= 16 && height >= 16, name + " must not downscale destroy pattern");
    }
  }

  private static String readEntry(ZipFile zip, String name) throws IOException {
    var entry = zip.getEntry(name);
    assertTrue(entry != null, "missing pack entry " + name);
    return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
  }

  private static String wireSuffix(int mask) {
    StringBuilder sb = new StringBuilder(6);
    if ((mask & 1) != 0) sb.append('u');
    if ((mask & 2) != 0) sb.append('d');
    if ((mask & 4) != 0) sb.append('n');
    if ((mask & 8) != 0) sb.append('s');
    if ((mask & 16) != 0) sb.append('e');
    if ((mask & 32) != 0) sb.append('w');
    return sb.toString();
  }

  private static boolean coversNorthFacePoint(
      com.google.gson.JsonObject model, double x, double y, double z) {
    for (var element : model.getAsJsonArray("elements")) {
      var object = element.getAsJsonObject();
      var faces = object.getAsJsonObject("faces");
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
      com.google.gson.JsonObject model, double[] expectedFrom, double[] expectedTo) {
    for (var element : model.getAsJsonArray("elements")) {
      var object = element.getAsJsonObject();
      if (arraysEqual(expectedFrom, doubleArray(object.getAsJsonArray("from")))
          && arraysEqual(expectedTo, doubleArray(object.getAsJsonArray("to")))) {
        return true;
      }
    }
    return false;
  }

  private static JsonObject faceWithBounds(
      JsonObject model, String face, double[] expectedFrom, double[] expectedTo) {
    for (var element : model.getAsJsonArray("elements")) {
      var object = element.getAsJsonObject();
      var faces = object.getAsJsonObject("faces");
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

  private static JsonObject firstFace(JsonObject model, String face) {
    for (var element : model.getAsJsonArray("elements")) {
      var object = element.getAsJsonObject();
      var faces = object.getAsJsonObject("faces");
      if (faces != null && faces.has(face)) {
        return faces.getAsJsonObject(face);
      }
    }
    throw new AssertionError("missing " + face + " face");
  }

  private static String texturePath(JsonObject textures, JsonObject face) {
    String texture = face.get("texture").getAsString();
    assertTrue(texture.startsWith("#"), "face must reference a texture slot: " + texture);
    return textures.get(texture.substring(1)).getAsString();
  }

  private static void assertAllFaceUvsInsideDestroySprite(com.google.gson.JsonObject model) {
    for (var element : model.getAsJsonArray("elements")) {
      var faces = element.getAsJsonObject().getAsJsonObject("faces");
      if (faces == null) {
        continue;
      }
      for (var entry : faces.entrySet()) {
        double[] uv = doubleArray(entry.getValue().getAsJsonObject().getAsJsonArray("uv"));
        for (double value : uv) {
          assertTrue(
              value >= 0.0 && value <= 16.0,
              () -> entry.getKey() + " face has out-of-sprite UV " + value);
        }
      }
    }
  }

  private static double[] doubleArray(com.google.gson.JsonArray array) {
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

  private static Set<String> pinnedLocales() throws IOException {
    Set<String> locales = new LinkedHashSet<>();
    locales.add("en_us");
    Files.readAllLines(
            Path.of("src/test/resources/minecraft-lang-locales.txt"), StandardCharsets.UTF_8)
        .stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .forEach(locales::add);
    return locales;
  }
}
