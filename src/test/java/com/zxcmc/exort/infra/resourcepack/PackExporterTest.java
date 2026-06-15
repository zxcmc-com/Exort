package com.zxcmc.exort.infra.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
      assertEntry(zip, "assets/exort/models/breaking/storage/core/stage_9.json");
      assertEntry(zip, "assets/exort/items/breaking/terminal/north/stage_3.json");
      assertEntry(zip, "assets/exort/models/breaking/bus/import/up/stage_9.json");
      assertEntry(zip, "assets/exort/models/breaking/bus/export/up/stage_9.json");
      assertEntry(zip, "assets/exort/items/breaking/bus/import/down/stage_0.json");
      assertEntry(zip, "assets/exort/items/breaking/bus/export/down/stage_0.json");
      assertEntry(zip, "assets/exort/items/breaking/bridge/bridge/stage_0.json");
      assertEntry(zip, "assets/exort/models/breaking/bridge/bridge/stage_9.json");
      assertEntry(zip, "assets/exort/items/breaking/wire/center/stage_0.json");
      assertEntry(zip, "assets/exort/items/breaking/wire/center/stage_1.json");
      assertEntry(zip, "assets/exort/items/breaking/wire/center/stage_2.json");
      assertEntry(zip, "assets/exort/models/breaking/wire/center/stage_0.json");
      assertEntry(zip, "assets/exort/models/breaking/wire/center/stage_1.json");
      assertEntry(zip, "assets/exort/models/breaking/wire/center/stage_2.json");
      assertEntry(zip, "assets/exort/textures/block/wire.png");
      assertEntry(zip, "assets/exort/textures/breaking/overlay/0.png");
      assertEntry(zip, "assets/exort/textures/breaking/overlay/9.png");
      assertEntry(zip, "assets/exort/textures/breaking/overlay/terminal.png");
      assertEntry(zip, "assets/exort/textures/breaking/particles/block.png");
      assertEntry(zip, "assets/exort/textures/breaking/particles/storage.png");
      assertEntry(zip, "assets/exort/textures/breaking/particles/wire.png");
      assertEquals(
          183,
          zip.stream()
              .filter(
                  entry ->
                      entry.getName().startsWith("assets/exort/models/breaking/")
                          && entry.getName().endsWith(".json"))
              .count());
      assertEquals(
          183,
          zip.stream()
              .filter(
                  entry ->
                      entry.getName().startsWith("assets/exort/items/breaking/")
                          && entry.getName().endsWith(".json"))
              .count());
      assertFalse(zip.getEntry("assets/exort/models/breaking/storage/south/stage_0.json") != null);
      assertFalse(zip.getEntry("assets/exort/items/breaking/storage/east/stage_0.json") != null);
      assertFalse(zip.getEntry("assets/exort/models/breaking/bus/up/stage_9.json") != null);
      assertFalse(zip.getEntry("assets/exort/items/breaking/bus/down/stage_0.json") != null);
      assertFalse(zip.getEntry("assets/exort/models/breaking/wire/dnsew/stage_5.json") != null);
      assertFalse(zip.getEntry("assets/exort/items/breaking/wire/center/stage_3.json") != null);
      assertFalse(zip.getEntry("assets/exort/items/breaking/wire/center/stage_9.json") != null);
      assertFalse(zip.getEntry("assets/exort/models/breaking/wire/center/stage_3.json") != null);
      assertFalse(zip.getEntry("assets/exort/models/breaking/wire/center/stage_9.json") != null);
      assertFalse(zip.getEntry("assets/exort/textures/breaking/" + "block.png") != null);
      assertFalse(zip.getEntry("assets/exort/textures/breaking/" + "storage.png") != null);
      assertFalse(zip.getEntry("assets/exort/textures/breaking/" + "wire.png") != null);
      assertFalse(zip.getEntry("assets/exort/textures/breaking/" + "terminal.png") != null);
      assertFalse(zip.getEntry("assets/exort/textures/" + "wires/glass.png") != null);
      assertFalse(
          zip.stream()
              .anyMatch(
                  entry ->
                      entry
                          .getName()
                          .startsWith("assets/exort/textures/breaking/" + "destroy_" + "stage_")));
      assertFalse(
          zip.stream()
              .anyMatch(
                  entry ->
                      entry.getName().startsWith("assets/exort/textures/breaking/generated/")));
      assertTerminalAtlas(zip);

      JsonObject storageBreakingModel =
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/storage/core/stage_9.json"))
              .getAsJsonObject();
      assertEquals(17, storageBreakingModel.getAsJsonArray("elements").size());
      assertEquals(45, faceCount(storageBreakingModel));
      assertAllFaceUvsInsideDestroySprite(storageBreakingModel);
      assertEquals(
          "exort:breaking/overlay/9",
          storageBreakingModel.getAsJsonObject("textures").get("0").getAsString());
      JsonObject wireStage2BreakingModel =
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/wire/center/stage_2.json"))
              .getAsJsonObject();
      assertEquals(
          "exort:breaking/overlay/2",
          wireStage2BreakingModel.getAsJsonObject("textures").get("0").getAsString());

      assertAllFaceUvsInsideDestroySprite(
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/bus/import/south/stage_0.json"))
              .getAsJsonObject());
      assertAllFaceUvsInsideDestroySprite(
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/bus/export/north/stage_0.json"))
              .getAsJsonObject());
      var busImportSouthBreakingModel =
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/bus/import/south/stage_0.json"))
              .getAsJsonObject();
      assertFalse(
          hasElementBounds(
              busImportSouthBreakingModel, new double[] {1, 1, 2}, new double[] {15, 15, 4}),
          "bus transition shelf must not receive breaking overlay cracks");
      JsonObject busImportProtrusion =
          faceWithBounds(
              busImportSouthBreakingModel,
              "north",
              new double[] {3, 3, -1},
              new double[] {13, 13, 2});
      assertTrue(
          arraysEqual(
              new double[] {2, 2, 14, 14}, doubleArray(busImportProtrusion.getAsJsonArray("uv"))),
          "bus protrusion face must use its 12px source texture span centered in the destroy"
              + " sprite");
      JsonObject busImportBodySide =
          faceWithBounds(
              busImportSouthBreakingModel,
              "east",
              new double[] {0, 0, 4},
              new double[] {16, 16, 16});
      assertTrue(
          arraysEqual(
              new double[] {14, 0, 2, 16}, doubleArray(busImportBodySide.getAsJsonArray("uv"))),
          "bus body side must center the destroy sprite on the 12px body depth instead of"
              + " compressing 16 crack pixels into that span");
      JsonObject busImportBodyTop =
          faceWithBounds(
              busImportSouthBreakingModel, "up", new double[] {0, 0, 4}, new double[] {16, 16, 16});
      assertTrue(
          arraysEqual(
              new double[] {16, 14, 0, 2}, doubleArray(busImportBodyTop.getAsJsonArray("uv"))),
          "bus body top must center the destroy sprite on the 12px body depth after rotation");
      assertEquals(
          "exort:breaking/overlay/0",
          texturePath(busImportSouthBreakingModel.getAsJsonObject("textures"), busImportBodySide),
          "bus breaking overlay must not use generated density textures");
      var busUpBreakingModel =
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/bus/import/up/stage_0.json"))
              .getAsJsonObject();
      var busDownBreakingModel =
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/bus/export/down/stage_0.json"))
              .getAsJsonObject();
      assertAllFaceUvsInsideDestroySprite(busUpBreakingModel);
      assertAllFaceUvsInsideDestroySprite(busDownBreakingModel);
      assertTrue(
          hasElementBounds(busUpBreakingModel, new double[] {3, 14, 3}, new double[] {13, 17, 13}),
          "bus/up breaking overlay must keep the connector overhang on the top side");
      assertFalse(
          hasElementBounds(busUpBreakingModel, new double[] {1, 12, 1}, new double[] {15, 14, 15}),
          "bus/up transition shelf must not receive breaking overlay cracks");
      JsonObject busUpConnectorTop =
          faceWithBounds(
              busUpBreakingModel, "up", new double[] {3, 14, 3}, new double[] {13, 17, 13});
      assertFalse(
          hasFaceBounds(
              busUpBreakingModel, "south", new double[] {3, 14, 3}, new double[] {13, 17, 13}),
          "bus/up connector side neck faces should not receive breaking overlay cracks");
      assertTrue(
          arraysEqual(
              new double[] {2, 2, 14, 14}, doubleArray(busUpConnectorTop.getAsJsonArray("uv"))),
          "bus/up connector top must use centered source-texture UVs");
      assertEquals(
          "exort:breaking/overlay/0",
          texturePath(busUpBreakingModel.getAsJsonObject("textures"), busUpConnectorTop),
          "bus/up large connector face must use the original destroy stage texture");
      assertTrue(
          hasElementBounds(busDownBreakingModel, new double[] {3, -1, 3}, new double[] {13, 2, 13}),
          "bus/down breaking overlay must keep the connector overhang on the bottom side");
      assertFalse(zip.getEntry("assets/exort/items/breaking/stage_0.json") != null);
      assertFalse(zip.getEntry("assets/exort/models/breaking/stage_0.json") != null);

      String atlas = readEntry(zip, "assets/minecraft/atlases/blocks.json");
      assertTrue(atlas.contains("\"resource\":\"exort:block/wire\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/particles/block\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/particles/storage\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/overlay/terminal\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/particles/wire\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/overlay/0\""));
      assertTrue(atlas.contains("\"resource\":\"exort:breaking/overlay/9\""));
      assertFalse(
          atlas.contains("\"resource\":\"exort:breaking/generated/\""),
          "breaking atlas must not reference generated destroy-stage textures");

      var terminalBreakingModel =
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/terminal/south/stage_0.json"))
              .getAsJsonObject();
      assertAllFaceUvsInsideDestroySprite(terminalBreakingModel);
      assertFalse(
          coversNorthFacePoint(terminalBreakingModel, 8, 8, 0),
          "terminal body transparent front cutout must not receive cracks");
      assertTrue(
          coversNorthFacePoint(terminalBreakingModel, 8, 8, 1),
          "terminal screen display face must still receive cracks");
      JsonObject terminalTextures = terminalBreakingModel.getAsJsonObject("textures");
      JsonObject screenFace =
          faceWithBounds(
              terminalBreakingModel, "north", new double[] {3, 3, 1}, new double[] {13, 13, 2});
      assertEquals(
          "exort:breaking/overlay/0",
          texturePath(terminalTextures, screenFace),
          "terminal display face must use the original destroy stage texture");
      assertTrue(
          arraysEqual(new double[] {3, 3, 13, 13}, doubleArray(screenFace.getAsJsonArray("uv"))),
          "terminal display face must keep world-aligned projected UVs");

      JsonObject bodyFace = firstFace(terminalBreakingModel, "south");
      assertEquals(
          "exort:breaking/overlay/0",
          texturePath(terminalTextures, bodyFace),
          "terminal body faces must use the original destroy stage texture");

      var terminalLateBreakingModel =
          JsonParser.parseString(
                  readEntry(zip, "assets/exort/models/breaking/terminal/south/stage_9.json"))
              .getAsJsonObject();
      JsonObject terminalLateTextures = terminalLateBreakingModel.getAsJsonObject("textures");
      JsonObject lateScreenFace =
          faceWithBounds(
              terminalLateBreakingModel, "north", new double[] {3, 3, 1}, new double[] {13, 13, 2});
      assertEquals(
          "exort:breaking/overlay/9",
          texturePath(terminalLateTextures, lateScreenFace),
          "terminal display face must keep using the full destroy stage texture on late stages");
      JsonObject lateFrameFace =
          faceWithBounds(
              terminalLateBreakingModel,
              "north",
              new double[] {0, 0, 0},
              new double[] {16, 16, 16});
      assertEquals(
          "exort:breaking/overlay/terminal",
          texturePath(terminalLateTextures, lateFrameFace),
          "late terminal front frame must use the masked terminal atlas");
      assertTrue(
          arraysEqual(new double[] {8, 8, 16, 16}, doubleArray(lateFrameFace.getAsJsonArray("uv"))),
          "stage 9 terminal frame must use the bottom-right terminal atlas tile");
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

  private static String readEntry(ZipFile zip, String name) throws IOException {
    var entry = zip.getEntry(name);
    assertTrue(entry != null, "missing pack entry " + name);
    return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
  }

  private static BufferedImage readPng(ZipFile zip, String name) throws IOException {
    var entry = zip.getEntry(name);
    assertTrue(entry != null, "missing pack entry " + name);
    BufferedImage image =
        ImageIO.read(new ByteArrayInputStream(zip.getInputStream(entry).readAllBytes()));
    assertTrue(image != null, "invalid PNG pack entry " + name);
    return image;
  }

  private static void assertTerminalAtlas(ZipFile zip) throws IOException {
    BufferedImage atlas = readPng(zip, "assets/exort/textures/breaking/overlay/terminal.png");
    assertEquals(32, atlas.getWidth());
    assertEquals(32, atlas.getHeight());
    for (int stage = 6; stage <= 9; stage++) {
      BufferedImage source =
          readPng(zip, "assets/exort/textures/breaking/overlay/" + stage + ".png");
      int tileX = ((stage - 6) % 2) * 16;
      int tileY = ((stage - 6) / 2) * 16;
      for (int y = 0; y < 16; y++) {
        for (int x = 0; x < 16; x++) {
          int expected = x >= 2 && x < 14 && y >= 2 && y < 14 ? 0 : source.getRGB(x, y);
          assertEquals(
              expected,
              atlas.getRGB(tileX + x, tileY + y),
              "terminal atlas mismatch at stage " + stage + " x=" + x + " y=" + y);
        }
      }
    }
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

  private static int faceCount(JsonObject model) {
    int count = 0;
    for (var element : model.getAsJsonArray("elements")) {
      var faces = element.getAsJsonObject().getAsJsonObject("faces");
      if (faces != null) {
        count += faces.size();
      }
    }
    return count;
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

  private static boolean hasFaceBounds(
      JsonObject model, String face, double[] expectedFrom, double[] expectedTo) {
    for (var element : model.getAsJsonArray("elements")) {
      var object = element.getAsJsonObject();
      var faces = object.getAsJsonObject("faces");
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
