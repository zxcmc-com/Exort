package com.zxcmc.exort.infra.resourcepack.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.zip.ZipEntry;
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
        assertEntry(zip, "assets/exort/lang/" + locale + ".json");
      }
    }
  }

  @Test
  void projectPackExportIncludesValidBreakingOverlayAssets() throws IOException {
    PackExporter.Result result =
        PackExporter.exportPack(
            Path.of("src/main/resources"),
            "pack/",
            tempDir.resolve("breaking-out").toFile(),
            Logger.getLogger("test"));

    assertTrue(result.available());
    try (ZipFile zip = new ZipFile(result.outputFile())) {
      assertRequiredBreakingEntries(zip);
      assertNoLegacyBreakingEntries(zip);
      assertBreakingModelsAreValid(zip);
      assertTerminalAtlasShape(zip);
      assertBreakingAtlasReferences(zip);
      assertChunkLoaderBreakingOverlayShape(zip);
      assertRelayItemDefinitionModels(zip);
      assertChunkLoaderItemDefinitionModels(zip);
      assertChunkLoaderTextureAndModelUvs(zip);
      assertTransmitterGuiOverlayAssets(zip);
    }
  }

  private static String langValue(Path pack, String entryName, String key) throws IOException {
    try (ZipFile zip = new ZipFile(pack.toFile())) {
      String json = readEntry(zip, entryName);
      return JsonParser.parseString(json).getAsJsonObject().get(key).getAsString();
    }
  }

  private static boolean hasKey(Path pack, String entryName, String key) throws IOException {
    try (ZipFile zip = new ZipFile(pack.toFile())) {
      String json = readEntry(zip, entryName);
      return JsonParser.parseString(json).getAsJsonObject().has(key);
    }
  }

  private static void assertRequiredBreakingEntries(ZipFile zip) {
    for (String entry :
        new String[] {
          "assets/exort/items/breaking/storage/core/stage_0.json",
          "assets/exort/models/breaking/storage/core/stage_9.json",
          "assets/exort/items/breaking/terminal/north/stage_3.json",
          "assets/exort/models/breaking/bus/import/up/stage_9.json",
          "assets/exort/models/breaking/bus/export/down/stage_9.json",
          "assets/exort/items/breaking/bus/import/down/stage_0.json",
          "assets/exort/items/breaking/bus/export/down/stage_0.json",
          "assets/exort/items/relay/relay.json",
          "assets/exort/items/relay/green.json",
          "assets/exort/items/relay/blue.json",
          "assets/exort/items/relay/red.json",
          "assets/exort/models/relay/black.json",
          "assets/exort/models/relay/green.json",
          "assets/exort/models/relay/blue.json",
          "assets/exort/models/relay/red.json",
          "assets/exort/items/breaking/relay/relay/stage_0.json",
          "assets/exort/models/breaking/relay/relay/stage_9.json",
          "assets/exort/items/chunkloader/chunkloader.json",
          "assets/exort/items/chunkloader/immortal.json",
          "assets/exort/items/chunkloader/mythical.json",
          "assets/exort/items/chunkloader/legendary.json",
          "assets/exort/items/chunkloader/disabled.json",
          "assets/exort/models/chunkloader/immortal.json",
          "assets/exort/models/chunkloader/mythical.json",
          "assets/exort/models/chunkloader/legendary.json",
          "assets/exort/models/chunkloader/disabled.json",
          "assets/exort/items/breaking/chunkloader/chunkloader/stage_0.json",
          "assets/exort/models/breaking/chunkloader/chunkloader/stage_9.json",
          "assets/exort/items/breaking/wire/center/stage_0.json",
          "assets/exort/items/breaking/wire/center/stage_1.json",
          "assets/exort/items/breaking/wire/center/stage_2.json",
          "assets/exort/models/breaking/wire/center/stage_0.json",
          "assets/exort/models/breaking/wire/center/stage_1.json",
          "assets/exort/models/breaking/wire/center/stage_2.json",
          "assets/exort/textures/block/wire.png",
          "assets/exort/textures/breaking/overlay/0.png",
          "assets/exort/textures/breaking/overlay/9.png",
          "assets/exort/textures/breaking/overlay/terminal.png",
          "assets/exort/textures/breaking/particles/block.png",
          "assets/exort/textures/breaking/particles/storage.png",
          "assets/exort/textures/breaking/particles/wire.png",
          "assets/exort/textures/block/relay.png",
          "assets/exort/font/default.json",
          "assets/exort/textures/gui/transmitter.png"
        }) {
      assertEntry(zip, entry);
    }
  }

  private static void assertTransmitterGuiOverlayAssets(ZipFile zip) throws IOException {
    JsonObject font =
        JsonParser.parseString(readEntry(zip, "assets/exort/font/default.json")).getAsJsonObject();
    JsonArray providers = font.getAsJsonArray("providers");
    boolean transmitterProvider = false;
    for (JsonElement provider : providers) {
      JsonObject object = provider.getAsJsonObject();
      JsonElement file = object.get("file");
      if (file != null && "exort:gui/transmitter.png".equals(file.getAsString())) {
        transmitterProvider = true;
        break;
      }
    }
    assertTrue(transmitterProvider, "font/default.json is missing transmitter GUI provider");
  }

  private static void assertNoLegacyBreakingEntries(ZipFile zip) {
    for (String entry :
        new String[] {
          "assets/exort/models/breaking/stage_0.json",
          "assets/exort/items/breaking/stage_0.json",
          "assets/exort/models/breaking/bus/up/stage_9.json",
          "assets/exort/items/breaking/bus/down/stage_0.json",
          "assets/exort/models/breaking/wire/center/stage_3.json",
          "assets/exort/items/chunk_loader/chunk_loader.json",
          "assets/exort/models/chunk_loader/chunk_loader.json",
          "assets/exort/models/chunkloader/chunkloader.json",
          "assets/exort/items/breaking/chunk_loader/chunk_loader/stage_0.json",
          "assets/exort/models/breaking/chunk_loader/chunk_loader/stage_9.json",
          "assets/exort/textures/breaking/block.png",
          "assets/exort/textures/breaking/storage.png",
          "assets/exort/textures/breaking/wire.png",
          "assets/exort/textures/wires/glass.png"
        }) {
      assertFalse(zip.getEntry(entry) != null, entry);
    }
    assertFalse(
        zip.stream()
            .anyMatch(
                entry -> entry.getName().startsWith("assets/exort/textures/breaking/generated/")));
  }

  private static void assertBreakingModelsAreValid(ZipFile zip) throws IOException {
    var entries =
        zip.stream()
            .filter(
                entry ->
                    entry.getName().startsWith("assets/exort/models/breaking/")
                        && entry.getName().endsWith(".json"))
            .toList();
    assertFalse(entries.isEmpty(), "no generated breaking models exported");
    for (ZipEntry entry : entries) {
      JsonObject model = JsonParser.parseString(readEntry(zip, entry.getName())).getAsJsonObject();
      assertFalse(model.getAsJsonArray("elements").isEmpty(), entry.getName());
      assertAllFaceUvsInsideDestroySprite(model, entry.getName());
      assertFaceTexturesResolve(model, entry.getName());
    }
  }

  private static void assertTerminalAtlasShape(ZipFile zip) throws IOException {
    BufferedImage atlas = readPng(zip, "assets/exort/textures/breaking/overlay/terminal.png");
    assertEquals(32, atlas.getWidth());
    assertEquals(32, atlas.getHeight());
  }

  private static void assertBreakingAtlasReferences(ZipFile zip) throws IOException {
    String atlas = readEntry(zip, "assets/minecraft/atlases/blocks.json");

    for (String resource :
        new String[] {
          "exort:block/wire",
          "exort:block/relay",
          "exort:breaking/particles/block",
          "exort:breaking/particles/storage",
          "exort:breaking/particles/wire",
          "exort:breaking/overlay/terminal",
          "exort:breaking/overlay/0",
          "exort:breaking/overlay/9"
        }) {
      assertTrue(atlas.contains("\"resource\":\"" + resource + "\""), resource);
    }
    assertFalse(atlas.contains("\"resource\":\"exort:breaking/generated/\""));
  }

  private static void assertChunkLoaderBreakingOverlayShape(ZipFile zip) throws IOException {
    JsonObject early =
        modelEntry(zip, "assets/exort/models/breaking/chunkloader/chunkloader/stage_0.json");
    assertTrue(hasElementBounds(early, new double[] {2, 2, 2}, new double[] {14, 14, 14}));
    assertEquals(6, faceCountForBounds(early, new double[] {2, 2, 2}, new double[] {14, 14, 14}));
    assertFalse(hasElementBounds(early, new double[] {0, 0, 0}, new double[] {16, 16, 16}));
    assertFalse(early.getAsJsonObject("textures").has("1"));

    JsonObject late =
        modelEntry(zip, "assets/exort/models/breaking/chunkloader/chunkloader/stage_9.json");
    assertTrue(hasElementBounds(late, new double[] {2, 2, 2}, new double[] {14, 14, 14}));
    assertEquals(6, faceCountForBounds(late, new double[] {2, 2, 2}, new double[] {14, 14, 14}));
    assertTrue(hasElementBounds(late, new double[] {0, 0, 0}, new double[] {16, 16, 16}));
    assertEquals(6, faceCountForBounds(late, new double[] {0, 0, 0}, new double[] {16, 16, 16}));
    assertEquals(
        "exort:breaking/overlay/terminal", late.getAsJsonObject("textures").get("1").getAsString());
  }

  private static void assertRelayItemDefinitionModels(ZipFile zip) throws IOException {
    assertItemDefinitionModel(zip, "assets/exort/items/relay/relay.json", "exort:relay/black");
    assertItemDefinitionModel(zip, "assets/exort/items/relay/green.json", "exort:relay/green");
    assertItemDefinitionModel(zip, "assets/exort/items/relay/blue.json", "exort:relay/blue");
    assertItemDefinitionModel(zip, "assets/exort/items/relay/red.json", "exort:relay/red");
  }

  private static void assertChunkLoaderItemDefinitionModels(ZipFile zip) throws IOException {
    assertItemDefinitionModel(
        zip, "assets/exort/items/chunkloader/chunkloader.json", "exort:chunkloader/immortal");
    assertItemDefinitionModel(
        zip, "assets/exort/items/chunkloader/immortal.json", "exort:chunkloader/immortal");
    assertItemDefinitionModel(
        zip, "assets/exort/items/chunkloader/mythical.json", "exort:chunkloader/mythical");
    assertItemDefinitionModel(
        zip, "assets/exort/items/chunkloader/legendary.json", "exort:chunkloader/legendary");
    assertItemDefinitionModel(
        zip, "assets/exort/items/chunkloader/disabled.json", "exort:chunkloader/disabled");
  }

  private static void assertChunkLoaderTextureAndModelUvs(ZipFile zip) throws IOException {
    BufferedImage texture = readPng(zip, "assets/exort/textures/block/chunkloader.png");
    assertEquals(32, texture.getWidth());
    assertEquals(32, texture.getHeight());
    for (String model :
        new String[] {
          "disabled.json", "immortal.json", "legendary.json", "mythical.json",
        }) {
      assertChunkLoaderTextureUvsStayInsidePaddedContent(
          modelEntry(zip, "assets/exort/models/chunkloader/" + model), model);
    }
  }

  private static void assertChunkLoaderTextureUvsStayInsidePaddedContent(
      JsonObject model, String entryName) {
    for (JsonElement element : model.getAsJsonArray("elements")) {
      JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
      if (faces == null) {
        continue;
      }
      for (var entry : faces.entrySet()) {
        JsonObject face = entry.getValue().getAsJsonObject();
        if (!"#3".equals(face.get("texture").getAsString())) {
          continue;
        }
        for (JsonElement value : face.getAsJsonArray("uv")) {
          double number = value.getAsDouble();
          assertTrue(
              number >= 2.0 && number <= 14.0,
              () -> entryName + " " + entry.getKey() + " UV outside padded content: " + number);
        }
      }
    }
  }

  private static void assertItemDefinitionModel(ZipFile zip, String entryName, String expected)
      throws IOException {
    JsonObject item = JsonParser.parseString(readEntry(zip, entryName)).getAsJsonObject();
    assertEquals(expected, item.getAsJsonObject("model").get("model").getAsString(), entryName);
  }

  private static void assertEntry(ZipFile zip, String name) {
    assertTrue(zip.getEntry(name) != null, "missing pack entry " + name);
  }

  private static String readEntry(ZipFile zip, String name) throws IOException {
    var entry = zip.getEntry(name);
    assertTrue(entry != null, "missing pack entry " + name);
    return new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
  }

  private static JsonObject modelEntry(ZipFile zip, String name) throws IOException {
    return JsonParser.parseString(readEntry(zip, name)).getAsJsonObject();
  }

  private static BufferedImage readPng(ZipFile zip, String name) throws IOException {
    var entry = zip.getEntry(name);
    assertTrue(entry != null, "missing pack entry " + name);
    BufferedImage image =
        ImageIO.read(new ByteArrayInputStream(zip.getInputStream(entry).readAllBytes()));
    assertTrue(image != null, "invalid PNG pack entry " + name);
    return image;
  }

  private static boolean hasElementBounds(
      JsonObject model, double[] expectedFrom, double[] expectedTo) {
    for (JsonElement element : model.getAsJsonArray("elements")) {
      JsonObject object = element.getAsJsonObject();
      if (arraysEqual(expectedFrom, doubleArray(object.getAsJsonArray("from")))
          && arraysEqual(expectedTo, doubleArray(object.getAsJsonArray("to")))) {
        return true;
      }
    }
    return false;
  }

  private static int faceCountForBounds(
      JsonObject model, double[] expectedFrom, double[] expectedTo) {
    for (JsonElement element : model.getAsJsonArray("elements")) {
      JsonObject object = element.getAsJsonObject();
      if (arraysEqual(expectedFrom, doubleArray(object.getAsJsonArray("from")))
          && arraysEqual(expectedTo, doubleArray(object.getAsJsonArray("to")))) {
        return object.getAsJsonObject("faces").size();
      }
    }
    return 0;
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

  private static void assertAllFaceUvsInsideDestroySprite(JsonObject model, String entryName) {
    for (JsonElement element : model.getAsJsonArray("elements")) {
      JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
      if (faces == null) {
        continue;
      }
      for (var entry : faces.entrySet()) {
        JsonArray uv = entry.getValue().getAsJsonObject().getAsJsonArray("uv");
        for (JsonElement value : uv) {
          double number = value.getAsDouble();
          assertTrue(
              number >= 0.0 && number <= 16.0,
              () -> entryName + " " + entry.getKey() + " UV out of sprite: " + number);
        }
      }
    }
  }

  private static void assertFaceTexturesResolve(JsonObject model, String entryName) {
    JsonObject textures = model.getAsJsonObject("textures");
    for (JsonElement element : model.getAsJsonArray("elements")) {
      JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
      if (faces == null) {
        continue;
      }
      for (var entry : faces.entrySet()) {
        String texture = entry.getValue().getAsJsonObject().get("texture").getAsString();
        assertTrue(texture.startsWith("#"), entryName + " " + texture);
        assertTrue(textures.has(texture.substring(1)), entryName + " " + texture);
      }
    }
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
