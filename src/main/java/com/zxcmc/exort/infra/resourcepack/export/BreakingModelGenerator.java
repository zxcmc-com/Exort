package com.zxcmc.exort.infra.resourcepack.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zxcmc.exort.display.core.DisplayRotation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.bukkit.block.BlockFace;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class BreakingModelGenerator {
  static final int STAGE_COUNT = 10;
  static final int TINT_COLOR = -6841698; // 0xFF979A9E

  private static final int WIRE_STAGE_COUNT = 3;
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  private static final String MODELS_ROOT = "assets/exort/models/";
  private static final String ITEMS_ROOT = "assets/exort/items/";
  private static final String TEXTURES_ROOT = "assets/exort/textures/";
  private static final String BREAKING_ROOT = "breaking/";
  private static final String BREAKING_OVERLAY_TEXTURE_ROOT = BREAKING_ROOT + "overlay/";
  private static final String SCREEN_TEXTURE_ROOT = "exort:display/";
  private static final int BASE_DESTROY_TEXTURE_SIZE = 16;
  private static final int TERMINAL_FRAME_FIRST_STAGE = 6;
  private static final int TERMINAL_FRAME_ATLAS_SIZE = 32;
  private static final int TERMINAL_FRAME_TILE_SIZE = 16;
  private static final int TERMINAL_FRAME_CUTOUT_MIN = 2;
  private static final int TERMINAL_FRAME_CUTOUT_MAX = 14;
  private static final double BLOCK_CENTER = 8.0;
  private static final double COORDINATE_EPSILON = 0.000001;
  private static final TextureSizeResolver NO_TEXTURE_SIZES = texture -> null;

  private BreakingModelGenerator() {}

  static int addGeneratedEntries(Logger logger, Map<String, byte[]> entries) {
    if (!hasBreakingSourceModels(entries)) {
      logger.log(Level.FINE, "No Exort source models found; breaking overlay generation skipped.");
      return 0;
    }

    List<VariantSource> variants = new ArrayList<>();
    addStorageVariants(entries, variants);
    addTerminalVariants(entries, variants);
    addBusVariants(entries, variants);
    addRelayVariants(entries, variants);
    addWireVariants(entries, variants);

    int added = addTerminalBreakingAtlas(entries);
    for (VariantSource variant : variants) {
      for (int stage = 0; stage < variant.stageCount(); stage++) {
        String itemPath =
            ITEMS_ROOT + BREAKING_ROOT + variant.modelKey() + "/stage_" + stage + ".json";
        String modelPath =
            MODELS_ROOT + BREAKING_ROOT + variant.modelKey() + "/stage_" + stage + ".json";
        if (!entries.containsKey(itemPath)) {
          added++;
        }
        entries.put(itemPath, itemJson(variant.modelKey(), stage).getBytes(StandardCharsets.UTF_8));
        if (!entries.containsKey(modelPath)) {
          added++;
        }
        entries.put(
            modelPath,
            GSON.toJson(
                    generateModel(
                        variant.sourceModel(),
                        stage,
                        variant.transform(),
                        variant.facePolicy(),
                        variant.textureSizes()))
                .getBytes(StandardCharsets.UTF_8));
      }
    }
    if (variants.isEmpty()) {
      logger.warning("No Exort breaking overlay models were generated.");
    } else {
      logger.log(
          Level.FINE, "Generated {0} Exort breaking overlay model variants.", variants.size());
    }
    return added;
  }

  private static boolean hasBreakingSourceModels(Map<String, byte[]> entries) {
    return entries.containsKey(MODELS_ROOT + "storage/storage.json")
        || entries.containsKey(MODELS_ROOT + "terminal/inventory.json")
        || entries.containsKey(MODELS_ROOT + "bus/import.json")
        || entries.containsKey(MODELS_ROOT + "relay/relay.json")
        || entries.containsKey(MODELS_ROOT + "wire/center.json");
  }

  static JsonObject generateModel(JsonObject sourceModel, int stage, Transform transform) {
    return generateModel(sourceModel, stage, transform, FacePolicy.DEFAULT);
  }

  static JsonObject generateModel(
      JsonObject sourceModel, int stage, Transform transform, Map<String, byte[]> entries) {
    return generateModel(sourceModel, stage, transform);
  }

  static JsonObject generateTerminalModel(JsonObject sourceModel, int stage, Transform transform) {
    return generateModel(sourceModel, stage, transform, FacePolicy.TERMINAL_SCREEN_FRONT);
  }

  static JsonObject generateBusModel(
      JsonObject sourceModel, int stage, Transform transform, Map<String, byte[]> entries) {
    return generateModel(
        sourceModel, stage, transform, FacePolicy.BUS, textureSizeResolver(entries));
  }

  private static JsonObject generateModel(
      JsonObject sourceModel, int stage, Transform transform, FacePolicy facePolicy) {
    return generateModel(sourceModel, stage, transform, facePolicy, NO_TEXTURE_SIZES);
  }

  private static JsonObject generateModel(
      JsonObject sourceModel,
      int stage,
      Transform transform,
      FacePolicy facePolicy,
      TextureSizeResolver textureSizes) {
    if (stage < 0 || stage >= STAGE_COUNT) {
      throw new IllegalArgumentException("Unsupported breaking stage: " + stage);
    }
    Objects.requireNonNull(sourceModel, "sourceModel");
    Objects.requireNonNull(transform, "transform");
    Objects.requireNonNull(facePolicy, "facePolicy");
    Objects.requireNonNull(textureSizes, "textureSizes");

    JsonObject root = new JsonObject();
    root.addProperty("format_version", "1.21.6");
    root.addProperty("credit", "phantomfighterxx");
    root.addProperty("ambientocclusion", false);

    JsonObject textures = new JsonObject();
    textures.addProperty("0", overlayTexture(stage));
    if (usesTerminalFrameAtlas(stage, facePolicy)) {
      textures.addProperty("1", terminalFrameOverlayTexture());
    }
    textures.addProperty("particle", overlayTexture(stage));
    root.add("textures", textures);

    JsonArray elements = new JsonArray();
    JsonArray sourceElements = sourceModel.getAsJsonArray("elements");
    JsonObject sourceTextures = sourceModel.getAsJsonObject("textures");
    if (sourceElements == null) {
      throw new IllegalArgumentException("Breaking overlay source model has no elements array.");
    }
    for (JsonElement sourceElement : sourceElements) {
      if (!sourceElement.isJsonObject()) {
        continue;
      }
      JsonObject element =
          transformElement(
              sourceElement.getAsJsonObject(), sourceTextures, transform, facePolicy, textureSizes);
      if (element != null) {
        elements.add(element);
      }
    }
    if (usesTerminalFrameAtlas(stage, facePolicy)) {
      JsonObject element = terminalFrameElement(stage, transform);
      if (element != null) {
        elements.add(element);
      }
    }
    root.add("elements", elements);
    return root;
  }

  private static int addTerminalBreakingAtlas(Map<String, byte[]> entries) {
    if (!entries.containsKey(MODELS_ROOT + "terminal/inventory.json")) {
      return 0;
    }
    boolean added = !entries.containsKey(terminalFrameOverlayTexturePath());
    entries.put(terminalFrameOverlayTexturePath(), terminalBreakingAtlas(entries));
    return added ? 1 : 0;
  }

  static Transform identityTransform() {
    return new Transform(new Quaternionf());
  }

  private static void addStorageVariants(
      Map<String, byte[]> entries, List<VariantSource> variants) {
    variants.add(
        new VariantSource(
            "storage/core",
            readModel(entries, "storage/storage.json"),
            identityTransform(),
            FacePolicy.DEFAULT,
            NO_TEXTURE_SIZES,
            STAGE_COUNT));
  }

  private static void addTerminalVariants(
      Map<String, byte[]> entries, List<VariantSource> variants) {
    JsonObject source = readModel(entries, "terminal/inventory.json");
    for (BlockFace face : horizontalFaces()) {
      variants.add(
          new VariantSource(
              "terminal/" + key(face),
              source,
              new Transform(DisplayRotation.rotationForFacing(face)),
              FacePolicy.TERMINAL_SCREEN_FRONT,
              NO_TEXTURE_SIZES,
              STAGE_COUNT));
    }
  }

  private static void addBusVariants(Map<String, byte[]> entries, List<VariantSource> variants) {
    TextureSizeResolver textureSizes = textureSizeResolver(entries);
    addBusVariants("import", readModel(entries, "bus/import.json"), textureSizes, variants);
    addBusVariants("export", readModel(entries, "bus/export.json"), textureSizes, variants);
  }

  private static void addBusVariants(
      String type,
      JsonObject source,
      TextureSizeResolver textureSizes,
      List<VariantSource> variants) {
    for (BlockFace face : fullFaces()) {
      variants.add(
          new VariantSource(
              "bus/" + type + "/" + key(face),
              source,
              new Transform(busBreakingRotationForFacing(face)),
              FacePolicy.BUS,
              textureSizes,
              STAGE_COUNT));
    }
  }

  private static Quaternionf busBreakingRotationForFacing(BlockFace face) {
    return switch (face) {
      case UP -> DisplayRotation.rotationForFacingFull(BlockFace.DOWN);
      case DOWN -> DisplayRotation.rotationForFacingFull(BlockFace.UP);
      default -> DisplayRotation.rotationForFacingFull(face);
    };
  }

  private static void addWireVariants(Map<String, byte[]> entries, List<VariantSource> variants) {
    variants.add(
        new VariantSource(
            "wire/center",
            readModel(entries, "wire/center.json"),
            identityTransform(),
            FacePolicy.DEFAULT,
            NO_TEXTURE_SIZES,
            WIRE_STAGE_COUNT));
  }

  private static void addRelayVariants(Map<String, byte[]> entries, List<VariantSource> variants) {
    variants.add(
        new VariantSource(
            "relay/relay",
            readModel(entries, "relay/relay.json"),
            identityTransform(),
            FacePolicy.DEFAULT,
            NO_TEXTURE_SIZES,
            STAGE_COUNT));
  }

  private static JsonObject transformElement(
      JsonObject source,
      JsonObject sourceTextures,
      Transform transform,
      FacePolicy facePolicy,
      TextureSizeResolver textureSizes) {
    validateUnsupportedElementRotation(source);
    double[] sourceFrom = readVector(source, "from");
    double[] sourceTo = readVector(source, "to");
    double[][] bounds = transformBounds(sourceFrom, sourceTo, transform.rotation());
    double[] from = bounds[0];
    double[] to = bounds[1];

    JsonObject faces = new JsonObject();
    JsonObject sourceFaces = source.getAsJsonObject("faces");
    if (sourceFaces != null) {
      for (Map.Entry<String, JsonElement> entry : sourceFaces.entrySet()) {
        JsonObject sourceFace =
            entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
        if (shouldSkipSourceFace(entry.getKey(), sourceFace, sourceTextures, facePolicy)) {
          continue;
        }
        if (shouldSkipBusOverlayFace(entry.getKey(), sourceFrom, sourceTo, facePolicy)) {
          continue;
        }
        String targetFace = rotateFace(entry.getKey(), transform.rotation());
        if (!isExternalFace(targetFace, from, to)) {
          continue;
        }
        JsonArray uv =
            uvForFace(targetFace, from, to, sourceFace, sourceTextures, facePolicy, textureSizes);
        if (uv != null) {
          faces.add(targetFace, faceJson(uv));
        }
      }
    }
    if (faces.size() == 0) {
      return null;
    }
    return targetElement(source, from, to, faces);
  }

  private static TextureSizeResolver textureSizeResolver(Map<String, byte[]> entries) {
    Objects.requireNonNull(entries, "entries");
    Map<String, TextureSize> cache = new HashMap<>();
    return texture -> {
      String path = texturePath(texture);
      if (path == null) {
        return null;
      }
      if (cache.containsKey(path)) {
        return cache.get(path);
      }
      byte[] raw = entries.get(path);
      if (raw == null) {
        return null;
      }
      try {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(raw));
        if (image == null) {
          throw new IllegalArgumentException("Invalid PNG breaking texture source: " + path);
        }
        TextureSize size = new TextureSize(image.getWidth(), image.getHeight());
        cache.put(path, size);
        return size;
      } catch (IOException e) {
        throw new IllegalArgumentException(
            "Failed to read PNG breaking texture source: " + path, e);
      }
    };
  }

  private static String texturePath(String texture) {
    if (texture == null || texture.isBlank() || texture.startsWith("#")) {
      return null;
    }
    String value = texture.trim();
    int separator = value.indexOf(':');
    String namespace = separator >= 0 ? value.substring(0, separator) : "minecraft";
    String path = separator >= 0 ? value.substring(separator + 1) : value;
    if (namespace.isBlank() || path.isBlank()) {
      return null;
    }
    return "assets/" + namespace + "/textures/" + path + ".png";
  }

  private static boolean shouldSkipSourceFace(
      String sourceFace,
      JsonObject sourceFaceObject,
      JsonObject sourceTextures,
      FacePolicy policy) {
    if (policy != FacePolicy.TERMINAL_SCREEN_FRONT || !"north".equals(sourceFace)) {
      return false;
    }
    return !resolveTexture(sourceFaceObject, sourceTextures).startsWith(SCREEN_TEXTURE_ROOT);
  }

  private static boolean shouldSkipBusOverlayFace(
      String sourceFace, double[] sourceFrom, double[] sourceTo, FacePolicy policy) {
    if (policy != FacePolicy.BUS) {
      return false;
    }
    if (isBusTransitionElement(sourceFrom, sourceTo)) {
      return true;
    }
    return isBusNeckElement(sourceFrom, sourceTo) && !"north".equals(sourceFace);
  }

  private static boolean isBusTransitionElement(double[] from, double[] to) {
    return hasSortedDimensions(from, to, 2.0, 14.0, 14.0);
  }

  private static boolean isBusNeckElement(double[] from, double[] to) {
    return hasSortedDimensions(from, to, 3.0, 10.0, 10.0);
  }

  private static boolean isBusBodyElement(double[] from, double[] to) {
    return hasSortedDimensions(from, to, 12.0, 16.0, 16.0);
  }

  private static boolean hasSortedDimensions(
      double[] from, double[] to, double first, double second, double third) {
    double[] dimensions =
        new double[] {
          Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]), Math.abs(to[2] - from[2])
        };
    Arrays.sort(dimensions);
    return Math.abs(dimensions[0] - first) < COORDINATE_EPSILON
        && Math.abs(dimensions[1] - second) < COORDINATE_EPSILON
        && Math.abs(dimensions[2] - third) < COORDINATE_EPSILON;
  }

  private static String resolveTexture(JsonObject sourceFace, JsonObject sourceTextures) {
    if (!sourceFace.has("texture") || !sourceFace.get("texture").isJsonPrimitive()) {
      return "";
    }
    String current = sourceFace.get("texture").getAsString();
    int guard = 0;
    while (current.startsWith("#") && sourceTextures != null && guard++ < 16) {
      JsonElement resolved = sourceTextures.get(current.substring(1));
      if (resolved == null || !resolved.isJsonPrimitive()) {
        return current;
      }
      current = resolved.getAsString();
    }
    return current;
  }

  private static JsonObject readModel(Map<String, byte[]> entries, String path) {
    byte[] raw = entries.get(MODELS_ROOT + path);
    if (raw == null) {
      throw new IllegalArgumentException(
          "Missing breaking overlay source model: " + MODELS_ROOT + path);
    }
    return JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
  }

  private static String itemJson(String modelKey, int stage) {
    return "{\"model\":{\"type\":\"model\",\"model\":\"exort:"
        + BREAKING_ROOT
        + modelKey
        + "/stage_"
        + stage
        + "\",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":"
        + TINT_COLOR
        + "}]}}\n";
  }

  private static void validateUnsupportedElementRotation(JsonObject element) {
    JsonObject rotation = element.getAsJsonObject("rotation");
    if (rotation == null || !rotation.has("angle")) {
      return;
    }
    double angle = rotation.get("angle").getAsDouble();
    if (Math.abs(angle) > COORDINATE_EPSILON) {
      throw new IllegalArgumentException(
          "Breaking overlay generator does not support non-zero element rotations: angle=" + angle);
    }
  }

  private static JsonObject faceJson(JsonArray uv) {
    return faceJson(uv, "#0");
  }

  private static JsonObject faceJson(JsonArray uv, String texture) {
    JsonObject face = new JsonObject();
    face.add("uv", uv);
    face.addProperty("texture", texture);
    face.addProperty("tintindex", 0);
    return face;
  }

  private static boolean usesTerminalFrameAtlas(int stage, FacePolicy facePolicy) {
    return facePolicy == FacePolicy.TERMINAL_SCREEN_FRONT && stage >= TERMINAL_FRAME_FIRST_STAGE;
  }

  private static JsonObject terminalFrameElement(int stage, Transform transform) {
    double[] sourceFrom = new double[] {0.0, 0.0, 0.0};
    double[] sourceTo = new double[] {16.0, 16.0, 16.0};
    double[][] bounds = transformBounds(sourceFrom, sourceTo, transform.rotation());
    double[] from = bounds[0];
    double[] to = bounds[1];
    String targetFace = rotateFace("north", transform.rotation());
    if (!isExternalFace(targetFace, from, to)) {
      return null;
    }

    JsonObject source = new JsonObject();
    source.addProperty("name", "terminal front frame");
    JsonObject faces = new JsonObject();
    faces.add(targetFace, faceJson(terminalFrameUv(stage), "#1"));
    return targetElement(source, from, to, faces);
  }

  private static JsonArray terminalFrameUv(int stage) {
    int atlasStage = stage - TERMINAL_FRAME_FIRST_STAGE;
    double uvScale = (double) BASE_DESTROY_TEXTURE_SIZE / TERMINAL_FRAME_ATLAS_SIZE;
    double tileX = (atlasStage % 2) * TERMINAL_FRAME_TILE_SIZE * uvScale;
    double tileY = (atlasStage / 2) * TERMINAL_FRAME_TILE_SIZE * uvScale;
    double tileSize = TERMINAL_FRAME_TILE_SIZE * uvScale;
    return numberArray(tileX, tileY, tileX + tileSize, tileY + tileSize);
  }

  static byte[] terminalBreakingAtlas(Map<String, byte[]> entries) {
    BufferedImage atlas =
        new BufferedImage(
            TERMINAL_FRAME_ATLAS_SIZE, TERMINAL_FRAME_ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
    for (int stage = TERMINAL_FRAME_FIRST_STAGE; stage < STAGE_COUNT; stage++) {
      BufferedImage source = readPng(entries, overlayStageTexturePath(stage));
      if (source.getWidth() != BASE_DESTROY_TEXTURE_SIZE
          || source.getHeight() != BASE_DESTROY_TEXTURE_SIZE) {
        throw new IllegalArgumentException(
            "Expected "
                + overlayStageTexturePath(stage)
                + " to be "
                + BASE_DESTROY_TEXTURE_SIZE
                + "x"
                + BASE_DESTROY_TEXTURE_SIZE
                + ", got "
                + source.getWidth()
                + "x"
                + source.getHeight());
      }
      int atlasStage = stage - TERMINAL_FRAME_FIRST_STAGE;
      int tileX = (atlasStage % 2) * TERMINAL_FRAME_TILE_SIZE;
      int tileY = (atlasStage / 2) * TERMINAL_FRAME_TILE_SIZE;
      for (int y = 0; y < BASE_DESTROY_TEXTURE_SIZE; y++) {
        for (int x = 0; x < BASE_DESTROY_TEXTURE_SIZE; x++) {
          int argb =
              x >= TERMINAL_FRAME_CUTOUT_MIN
                      && x < TERMINAL_FRAME_CUTOUT_MAX
                      && y >= TERMINAL_FRAME_CUTOUT_MIN
                      && y < TERMINAL_FRAME_CUTOUT_MAX
                  ? 0
                  : source.getRGB(x, y);
          atlas.setRGB(tileX + x, tileY + y, argb);
        }
      }
    }
    return writePng(atlas, terminalFrameOverlayTexturePath());
  }

  private static BufferedImage readPng(Map<String, byte[]> entries, String path) {
    byte[] raw = entries.get(path);
    if (raw == null) {
      throw new IllegalArgumentException("Missing breaking texture source: " + path);
    }
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(raw));
      if (image == null) {
        throw new IllegalArgumentException("Invalid PNG breaking texture: " + path);
      }
      return image;
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to read PNG breaking texture: " + path, e);
    }
  }

  private static byte[] writePng(BufferedImage image, String path) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (!ImageIO.write(image, "png", out)) {
        throw new IllegalStateException("PNG writer is not available for " + path);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write PNG breaking texture: " + path, e);
    }
  }

  private static JsonObject targetElement(
      JsonObject source, double[] from, double[] to, JsonObject faces) {
    JsonObject target = new JsonObject();
    if (source.has("name") && source.get("name").isJsonPrimitive()) {
      target.addProperty("name", source.get("name").getAsString());
    }
    target.add("from", numberArray(from[0], from[1], from[2]));
    target.add("to", numberArray(to[0], to[1], to[2]));
    target.addProperty("shade", false);
    target.add("faces", faces);
    return target;
  }

  private static double[] readVector(JsonObject object, String key) {
    JsonArray array = object.getAsJsonArray(key);
    if (array == null || array.size() != 3) {
      throw new IllegalArgumentException("Model element has invalid " + key + " vector.");
    }
    return new double[] {
      array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()
    };
  }

  private static double[][] transformBounds(double[] from, double[] to, Quaternionf rotation) {
    double[] min =
        new double[] {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
    double[] max =
        new double[] {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
    for (double x : new double[] {from[0], to[0]}) {
      for (double y : new double[] {from[1], to[1]}) {
        for (double z : new double[] {from[2], to[2]}) {
          double[] transformed = transformPoint(rotation, x, y, z);
          for (int axis = 0; axis < 3; axis++) {
            min[axis] = Math.min(min[axis], transformed[axis]);
            max[axis] = Math.max(max[axis], transformed[axis]);
          }
        }
      }
    }
    return new double[][] {min, max};
  }

  private static double[] transformPoint(Quaternionf rotation, double x, double y, double z) {
    Vector3f vector =
        new Vector3f(
            (float) (x - BLOCK_CENTER), (float) (y - BLOCK_CENTER), (float) (z - BLOCK_CENTER));
    rotation.transform(vector);
    return new double[] {
      cleanCoordinate(vector.x + BLOCK_CENTER),
      cleanCoordinate(vector.y + BLOCK_CENTER),
      cleanCoordinate(vector.z + BLOCK_CENTER)
    };
  }

  private static String rotateFace(String sourceFace, Quaternionf rotation) {
    Vector3f normal = normal(sourceFace);
    rotation.transform(normal);
    return faceName(normal);
  }

  private static Vector3f normal(String face) {
    return switch (face) {
      case "north" -> new Vector3f(0f, 0f, -1f);
      case "south" -> new Vector3f(0f, 0f, 1f);
      case "east" -> new Vector3f(1f, 0f, 0f);
      case "west" -> new Vector3f(-1f, 0f, 0f);
      case "up" -> new Vector3f(0f, 1f, 0f);
      case "down" -> new Vector3f(0f, -1f, 0f);
      default -> throw new IllegalArgumentException("Unsupported model face: " + face);
    };
  }

  private static String faceName(Vector3f normal) {
    float x = Math.abs(normal.x);
    float y = Math.abs(normal.y);
    float z = Math.abs(normal.z);
    if (x >= y && x >= z) {
      return normal.x >= 0f ? "east" : "west";
    }
    if (y >= x && y >= z) {
      return normal.y >= 0f ? "up" : "down";
    }
    return normal.z >= 0f ? "south" : "north";
  }

  private static boolean isExternalFace(String face, double[] from, double[] to) {
    return switch (face) {
      case "north" -> outwardDistance(from[2], -1.0) >= -COORDINATE_EPSILON;
      case "south" -> outwardDistance(to[2], 1.0) >= -COORDINATE_EPSILON;
      case "west" -> outwardDistance(from[0], -1.0) >= -COORDINATE_EPSILON;
      case "east" -> outwardDistance(to[0], 1.0) >= -COORDINATE_EPSILON;
      case "down" -> outwardDistance(from[1], -1.0) >= -COORDINATE_EPSILON;
      case "up" -> outwardDistance(to[1], 1.0) >= -COORDINATE_EPSILON;
      default -> throw new IllegalArgumentException("Unsupported model face: " + face);
    };
  }

  private static double outwardDistance(double coordinate, double normalSign) {
    return (coordinate - BLOCK_CENTER) * normalSign;
  }

  private static JsonArray uvForFace(
      String face,
      double[] from,
      double[] to,
      JsonObject sourceFace,
      JsonObject sourceTextures,
      FacePolicy facePolicy,
      TextureSizeResolver textureSizes) {
    if (facePolicy == FacePolicy.BUS) {
      if (isBusBodyElement(from, to)) {
        return centeredUvForFace(face, from, to);
      }
      JsonArray uv = busUvForSourceFace(sourceFace, sourceTextures, textureSizes);
      return uv == null ? centeredUvForFace(face, from, to) : uv;
    }
    return projectedUvForFace(face, from, to);
  }

  private static JsonArray projectedUvForFace(String face, double[] from, double[] to) {
    return switch (face) {
      case "north", "south" -> {
        if (!intersectsDestroySprite(from[0], to[0]) || !intersectsDestroySprite(from[1], to[1])) {
          yield null;
        }
        yield fittedNumberArray(from[0], 16.0 - to[1], to[0], 16.0 - from[1]);
      }
      case "east", "west" -> {
        if (!intersectsDestroySprite(from[2], to[2]) || !intersectsDestroySprite(from[1], to[1])) {
          yield null;
        }
        yield fittedNumberArray(16.0 - from[2], 16.0 - to[1], 16.0 - to[2], 16.0 - from[1]);
      }
      case "up", "down" -> {
        if (!intersectsDestroySprite(from[0], to[0]) || !intersectsDestroySprite(from[2], to[2])) {
          yield null;
        }
        yield fittedNumberArray(16.0 - from[0], 16.0 - from[2], 16.0 - to[0], 16.0 - to[2]);
      }
      default -> throw new IllegalArgumentException("Unsupported model face: " + face);
    };
  }

  private static JsonArray busUvForSourceFace(
      JsonObject sourceFace, JsonObject sourceTextures, TextureSizeResolver textureSizes) {
    if (sourceFace == null || !sourceFace.has("uv")) {
      return null;
    }
    JsonArray sourceUv = sourceFace.getAsJsonArray("uv");
    if (sourceUv == null || sourceUv.size() != 4) {
      return null;
    }
    TextureSize textureSize = textureSizes.resolve(resolveTexture(sourceFace, sourceTextures));
    if (textureSize == null) {
      return null;
    }

    double u1 = sourceUv.get(0).getAsDouble();
    double v1 = sourceUv.get(1).getAsDouble();
    double u2 = sourceUv.get(2).getAsDouble();
    double v2 = sourceUv.get(3).getAsDouble();
    double width = Math.abs(u2 - u1) * textureSize.width() / BASE_DESTROY_TEXTURE_SIZE;
    double height = Math.abs(v2 - v1) * textureSize.height() / BASE_DESTROY_TEXTURE_SIZE;
    boolean reverseU = u2 < u1;
    boolean reverseV = v2 < v1;

    int rotation = sourceFaceRotation(sourceFace);
    if (rotation == 90 || rotation == 270) {
      double tmpSize = width;
      width = height;
      height = tmpSize;
      boolean tmpReverse = reverseU;
      reverseU = reverseV;
      reverseV = tmpReverse;
    }
    if (width <= COORDINATE_EPSILON || height <= COORDINATE_EPSILON) {
      return null;
    }
    double[] u = centeredDestroyUvInterval(width, reverseU);
    double[] v = centeredDestroyUvInterval(height, reverseV);
    return numberArray(u[0], v[0], u[1], v[1]);
  }

  private static int sourceFaceRotation(JsonObject sourceFace) {
    if (!sourceFace.has("rotation") || !sourceFace.get("rotation").isJsonPrimitive()) {
      return 0;
    }
    int rotation = Math.floorMod(sourceFace.get("rotation").getAsInt(), 360);
    return rotation == 90 || rotation == 180 || rotation == 270 ? rotation : 0;
  }

  private static JsonArray centeredUvForFace(String face, double[] from, double[] to) {
    return switch (face) {
      case "north", "south" -> centeredNumberArray(to[0] - from[0], to[1] - from[1], false, false);
      case "east", "west" -> centeredNumberArray(to[2] - from[2], to[1] - from[1], true, false);
      case "up", "down" -> centeredNumberArray(to[0] - from[0], to[2] - from[2], true, true);
      default -> throw new IllegalArgumentException("Unsupported model face: " + face);
    };
  }

  private static JsonArray centeredNumberArray(
      double width, double height, boolean reverseU, boolean reverseV) {
    double[] u = centeredDestroyUvInterval(width, reverseU);
    double[] v = centeredDestroyUvInterval(height, reverseV);
    return numberArray(u[0], v[0], u[1], v[1]);
  }

  private static double[] centeredDestroyUvInterval(double size, boolean reverse) {
    double length = Math.max(0.0, Math.min(BASE_DESTROY_TEXTURE_SIZE, size));
    double start = (BASE_DESTROY_TEXTURE_SIZE - length) / 2.0;
    double end = start + length;
    return reverse ? new double[] {end, start} : new double[] {start, end};
  }

  private static JsonArray fittedNumberArray(double u1, double v1, double u2, double v2) {
    double[] u = fittedDestroyUvInterval(u1, u2);
    double[] v = fittedDestroyUvInterval(v1, v2);
    return numberArray(u[0], v[0], u[1], v[1]);
  }

  private static double[] fittedDestroyUvInterval(double first, double second) {
    double min = Math.min(first, second);
    double max = Math.max(first, second);
    if (max - min > BASE_DESTROY_TEXTURE_SIZE + COORDINATE_EPSILON) {
      return new double[] {
        Math.max(0.0, Math.min(BASE_DESTROY_TEXTURE_SIZE, first)),
        Math.max(0.0, Math.min(BASE_DESTROY_TEXTURE_SIZE, second))
      };
    }
    double offset = 0.0;
    if (min < 0.0) {
      offset = -min;
    } else if (max > BASE_DESTROY_TEXTURE_SIZE) {
      offset = BASE_DESTROY_TEXTURE_SIZE - max;
    }
    return new double[] {first + offset, second + offset};
  }

  private static boolean intersectsDestroySprite(double first, double second) {
    double min = Math.min(first, second);
    double max = Math.max(first, second);
    return Math.min(max, 16.0) - Math.max(min, 0.0) > COORDINATE_EPSILON;
  }

  private static JsonArray numberArray(double... values) {
    JsonArray array = new JsonArray();
    for (double value : values) {
      double clean = cleanCoordinate(value);
      if (Math.abs(clean - Math.rint(clean)) < COORDINATE_EPSILON) {
        array.add((int) Math.rint(clean));
      } else {
        array.add(clean);
      }
    }
    return array;
  }

  private static double cleanCoordinate(double value) {
    double rounded = Math.rint(value * 1_000_000.0) / 1_000_000.0;
    return Math.abs(rounded) < COORDINATE_EPSILON ? 0.0 : rounded;
  }

  private static List<BlockFace> horizontalFaces() {
    return List.of(BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST);
  }

  private static List<BlockFace> fullFaces() {
    return List.of(
        BlockFace.SOUTH,
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.WEST,
        BlockFace.UP,
        BlockFace.DOWN);
  }

  private static String key(BlockFace face) {
    return face.name().toLowerCase(Locale.ROOT);
  }

  private static String overlayTexture(int stage) {
    return "exort:" + BREAKING_OVERLAY_TEXTURE_ROOT + stage;
  }

  private static String terminalFrameOverlayTexture() {
    return "exort:" + BREAKING_OVERLAY_TEXTURE_ROOT + "terminal";
  }

  private static String terminalFrameOverlayTexturePath() {
    return TEXTURES_ROOT + BREAKING_OVERLAY_TEXTURE_ROOT + "terminal.png";
  }

  private static String overlayStageTexturePath(int stage) {
    return TEXTURES_ROOT + BREAKING_OVERLAY_TEXTURE_ROOT + stage + ".png";
  }

  record Transform(Quaternionf rotation) {
    Transform {
      rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
    }
  }

  private enum FacePolicy {
    DEFAULT,
    TERMINAL_SCREEN_FRONT,
    BUS
  }

  @FunctionalInterface
  private interface TextureSizeResolver {
    TextureSize resolve(String texture);
  }

  private record TextureSize(int width, int height) {}

  private record VariantSource(
      String modelKey,
      JsonObject sourceModel,
      Transform transform,
      FacePolicy facePolicy,
      TextureSizeResolver textureSizes,
      int stageCount) {}
}
