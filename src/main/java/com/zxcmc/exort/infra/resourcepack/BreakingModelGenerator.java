package com.zxcmc.exort.infra.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zxcmc.exort.display.DisplayRotation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
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

  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  private static final String MODELS_ROOT = "assets/exort/models/";
  private static final String ITEMS_ROOT = "assets/exort/items/";
  private static final String BREAKING_ROOT = "breaking/";
  private static final double BLOCK_CENTER = 8.0;
  private static final double COORDINATE_EPSILON = 0.000001;

  private BreakingModelGenerator() {}

  static int addGeneratedEntries(Logger logger, Map<String, byte[]> entries) {
    if (!hasBreakingSourceModels(entries)) {
      logger.log(Level.FINE, "No Exort source models found; breaking overlay generation skipped.");
      return 0;
    }
    TextureAlphaIndex textureAlphaIndex = new TextureAlphaIndex(entries);
    List<VariantSource> variants = new ArrayList<>();
    addStorageVariants(entries, variants);
    addTerminalVariants(entries, variants);
    addBusVariants(entries, variants);
    addWireVariants(entries, variants);

    int added = 0;
    for (VariantSource variant : variants) {
      for (int stage = 0; stage < STAGE_COUNT; stage++) {
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
                        variant.sourceModel(), stage, variant.transform(), textureAlphaIndex))
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
        || entries.keySet().stream()
            .anyMatch(path -> path.startsWith(MODELS_ROOT + "wire/") && path.endsWith(".json"));
  }

  static JsonObject generateModel(JsonObject sourceModel, int stage, Transform transform) {
    return generateModel(sourceModel, stage, transform, TextureAlphaIndex.empty());
  }

  static JsonObject generateModel(
      JsonObject sourceModel, int stage, Transform transform, Map<String, byte[]> entries) {
    return generateModel(sourceModel, stage, transform, new TextureAlphaIndex(entries));
  }

  private static JsonObject generateModel(
      JsonObject sourceModel, int stage, Transform transform, TextureAlphaIndex textureAlphaIndex) {
    if (stage < 0 || stage >= STAGE_COUNT) {
      throw new IllegalArgumentException("Unsupported breaking stage: " + stage);
    }
    Objects.requireNonNull(sourceModel, "sourceModel");
    Objects.requireNonNull(transform, "transform");
    Objects.requireNonNull(textureAlphaIndex, "textureAlphaIndex");

    String texture = "exort:breaking/destroy_stage_" + stage;
    JsonObject root = new JsonObject();
    root.addProperty("format_version", "1.21.6");
    root.addProperty("credit", "phantomfighterxx");
    root.addProperty("ambientocclusion", false);

    JsonObject textures = new JsonObject();
    textures.addProperty("0", texture);
    textures.addProperty("particle", texture);
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
      for (JsonObject element :
          transformElement(
              sourceElement.getAsJsonObject(), sourceTextures, transform, textureAlphaIndex)) {
        elements.add(element);
      }
    }
    root.add("elements", elements);
    return root;
  }

  static Transform identityTransform() {
    return new Transform(new Quaternionf());
  }

  private static void addStorageVariants(
      Map<String, byte[]> entries, List<VariantSource> variants) {
    JsonObject source = readModel(entries, "storage/storage.json");
    variants.add(new VariantSource("storage/core", source, identityTransform()));
    for (BlockFace face : horizontalFaces()) {
      variants.add(
          new VariantSource(
              "storage/" + key(face),
              source,
              new Transform(DisplayRotation.rotationForFacing(face))));
    }
  }

  private static void addTerminalVariants(
      Map<String, byte[]> entries, List<VariantSource> variants) {
    JsonObject source = readModel(entries, "terminal/inventory.json");
    for (BlockFace face : horizontalFaces()) {
      variants.add(
          new VariantSource(
              "terminal/" + key(face),
              source,
              new Transform(DisplayRotation.rotationForFacing(face))));
    }
  }

  private static void addBusVariants(Map<String, byte[]> entries, List<VariantSource> variants) {
    JsonObject source = readModel(entries, "bus/import.json");
    for (BlockFace face : fullFaces()) {
      variants.add(
          new VariantSource(
              "bus/" + key(face), source, new Transform(busBreakingRotationForFacing(face))));
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
    Transform mirrorY180 = new Transform(new Quaternionf().rotateY((float) Math.PI));
    entries.keySet().stream()
        .filter(path -> path.startsWith(MODELS_ROOT + "wire/"))
        .filter(path -> path.endsWith(".json"))
        .sorted(Comparator.naturalOrder())
        .forEach(
            path -> {
              String fileName = path.substring((MODELS_ROOT + "wire/").length());
              String key = fileName.substring(0, fileName.length() - ".json".length());
              variants.add(
                  new VariantSource(
                      "wire/" + key, readModel(entries, "wire/" + fileName), mirrorY180));
            });
  }

  private static List<JsonObject> transformElement(
      JsonObject source,
      JsonObject sourceTextures,
      Transform transform,
      TextureAlphaIndex textureAlphaIndex) {
    validateUnsupportedElementRotation(source);
    double[] sourceFrom = readVector(source, "from");
    double[] sourceTo = readVector(source, "to");
    double[][] bounds = transformBounds(sourceFrom, sourceTo, transform.rotation());
    double[] from = bounds[0];
    double[] to = bounds[1];

    List<JsonObject> targetElements = new ArrayList<>();
    JsonObject baseFaces = new JsonObject();
    JsonObject sourceFaces = source.getAsJsonObject("faces");
    if (sourceFaces != null) {
      for (Map.Entry<String, JsonElement> entry : sourceFaces.entrySet()) {
        String targetFace = rotateFace(entry.getKey(), transform.rotation());
        JsonObject sourceFace =
            entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
        FaceCoverage coverage = faceCoverage(sourceFace, sourceTextures, textureAlphaIndex);
        if (coverage.transparent()) {
          continue;
        }
        if (coverage.opaque()) {
          double[][] clipped = clipProjectedFaceBounds(targetFace, from, to);
          if (clipped == null) {
            continue;
          }
          if (sameBounds(from, to, clipped[0], clipped[1])) {
            baseFaces.add(targetFace, faceJson(targetFace, from, to));
          } else {
            JsonObject faces = new JsonObject();
            faces.add(targetFace, faceJson(targetFace, clipped[0], clipped[1]));
            targetElements.add(
                targetElement(
                    source, clipped[0], clipped[1], faces, " " + targetFace + " clipped"));
          }
          continue;
        }
        int rectIndex = 0;
        for (FaceRect rect : coverage.rects()) {
          double[][] sourceSubBounds = subBoundsForFace(entry.getKey(), sourceFrom, sourceTo, rect);
          double[][] transformedSubBounds =
              transformBounds(sourceSubBounds[0], sourceSubBounds[1], transform.rotation());
          double[][] clipped =
              clipProjectedFaceBounds(targetFace, transformedSubBounds[0], transformedSubBounds[1]);
          if (clipped == null) {
            continue;
          }
          JsonObject faces = new JsonObject();
          faces.add(targetFace, faceJson(targetFace, clipped[0], clipped[1]));
          targetElements.add(
              targetElement(
                  source,
                  clipped[0],
                  clipped[1],
                  faces,
                  " " + targetFace + " alpha " + rectIndex++));
        }
      }
    }
    if (baseFaces.size() > 0) {
      targetElements.add(0, targetElement(source, from, to, baseFaces, ""));
    }
    return targetElements;
  }

  private static double[][] clipProjectedFaceBounds(String face, double[] from, double[] to) {
    double[] clippedFrom = from.clone();
    double[] clippedTo = to.clone();
    // Item model UVs sample atlas sprites directly. Clipping projected crack UVs keeps
    // overhanging geometry from reading neighbouring or missing atlas regions.
    switch (face) {
      case "north", "south" -> {
        if (!clipAxis(clippedFrom, clippedTo, 0) || !clipAxis(clippedFrom, clippedTo, 1)) {
          return null;
        }
      }
      case "east", "west" -> {
        if (!clipAxis(clippedFrom, clippedTo, 2) || !clipAxis(clippedFrom, clippedTo, 1)) {
          return null;
        }
      }
      case "up", "down" -> {
        if (!clipAxis(clippedFrom, clippedTo, 0) || !clipAxis(clippedFrom, clippedTo, 2)) {
          return null;
        }
      }
      default -> throw new IllegalArgumentException("Unsupported model face: " + face);
    }
    return new double[][] {clippedFrom, clippedTo};
  }

  private static boolean clipAxis(double[] from, double[] to, int axis) {
    from[axis] = Math.max(0.0, from[axis]);
    to[axis] = Math.min(16.0, to[axis]);
    return to[axis] - from[axis] > COORDINATE_EPSILON;
  }

  private static boolean sameBounds(
      double[] from, double[] to, double[] otherFrom, double[] otherTo) {
    for (int axis = 0; axis < 3; axis++) {
      if (Math.abs(from[axis] - otherFrom[axis]) > COORDINATE_EPSILON
          || Math.abs(to[axis] - otherTo[axis]) > COORDINATE_EPSILON) {
        return false;
      }
    }
    return true;
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

  private static FaceCoverage faceCoverage(
      JsonObject sourceFace, JsonObject sourceTextures, TextureAlphaIndex textureAlphaIndex) {
    int rotation = faceTextureRotation(sourceFace);
    if (!sourceFace.has("texture") || !sourceFace.has("uv")) {
      return FaceCoverage.allOpaque();
    }
    String texture = resolveTexture(sourceFace.get("texture").getAsString(), sourceTextures);
    AlphaImage alpha = textureAlphaIndex.image(texture);
    if (alpha == null) {
      return FaceCoverage.allOpaque();
    }
    double[] uv = readUv(sourceFace);
    return FaceCoverage.fromAlpha(alpha, uv, rotation);
  }

  private static int faceTextureRotation(JsonObject sourceFace) {
    if (!sourceFace.has("rotation")) {
      return 0;
    }
    int rotation = sourceFace.get("rotation").getAsInt();
    return switch (rotation) {
      case 0, 90, 180, 270 -> rotation;
      default ->
          throw new IllegalArgumentException("Unsupported face texture rotation: " + rotation);
    };
  }

  private static String resolveTexture(String texture, JsonObject sourceTextures) {
    if (texture == null || texture.isBlank()) {
      return "";
    }
    String current = texture.trim();
    int guard = 0;
    while (current.startsWith("#") && sourceTextures != null && guard++ < 16) {
      String key = current.substring(1);
      JsonElement resolved = sourceTextures.get(key);
      if (resolved == null || !resolved.isJsonPrimitive()) {
        return current;
      }
      current = resolved.getAsString();
    }
    return current;
  }

  private static double[] readUv(JsonObject sourceFace) {
    JsonArray uv = sourceFace.getAsJsonArray("uv");
    if (uv == null || uv.size() != 4) {
      throw new IllegalArgumentException("Model face has invalid uv vector.");
    }
    return new double[] {
      uv.get(0).getAsDouble(),
      uv.get(1).getAsDouble(),
      uv.get(2).getAsDouble(),
      uv.get(3).getAsDouble()
    };
  }

  private static JsonObject faceJson(String targetFace, double[] from, double[] to) {
    JsonObject face = new JsonObject();
    face.add("uv", uvForFace(targetFace, from, to));
    face.addProperty("texture", "#0");
    face.addProperty("tintindex", 0);
    return face;
  }

  private static JsonObject targetElement(
      JsonObject source, double[] from, double[] to, JsonObject faces, String nameSuffix) {
    JsonObject target = new JsonObject();
    if (source.has("name") && source.get("name").isJsonPrimitive()) {
      target.addProperty("name", source.get("name").getAsString() + nameSuffix);
    }
    target.add("from", numberArray(from[0], from[1], from[2]));
    target.add("to", numberArray(to[0], to[1], to[2]));
    target.addProperty("shade", false);
    target.add("faces", faces);
    return target;
  }

  private static double[][] subBoundsForFace(
      String face, double[] from, double[] to, FaceRect rect) {
    double[] subFrom = from.clone();
    double[] subTo = to.clone();
    switch (face) {
      case "north" -> {
        setInterval(
            subFrom,
            subTo,
            0,
            lerp(from[0], to[0], rect.sMin()),
            lerp(from[0], to[0], rect.sMax()));
        setInterval(
            subFrom,
            subTo,
            1,
            lerp(to[1], from[1], rect.tMin()),
            lerp(to[1], from[1], rect.tMax()));
      }
      case "south" -> {
        setInterval(
            subFrom,
            subTo,
            0,
            lerp(to[0], from[0], rect.sMin()),
            lerp(to[0], from[0], rect.sMax()));
        setInterval(
            subFrom,
            subTo,
            1,
            lerp(to[1], from[1], rect.tMin()),
            lerp(to[1], from[1], rect.tMax()));
      }
      case "east" -> {
        setInterval(
            subFrom,
            subTo,
            2,
            lerp(from[2], to[2], rect.sMin()),
            lerp(from[2], to[2], rect.sMax()));
        setInterval(
            subFrom,
            subTo,
            1,
            lerp(to[1], from[1], rect.tMin()),
            lerp(to[1], from[1], rect.tMax()));
      }
      case "west" -> {
        setInterval(
            subFrom,
            subTo,
            2,
            lerp(to[2], from[2], rect.sMin()),
            lerp(to[2], from[2], rect.sMax()));
        setInterval(
            subFrom,
            subTo,
            1,
            lerp(to[1], from[1], rect.tMin()),
            lerp(to[1], from[1], rect.tMax()));
      }
      case "up" -> {
        setInterval(
            subFrom,
            subTo,
            0,
            lerp(from[0], to[0], rect.sMin()),
            lerp(from[0], to[0], rect.sMax()));
        setInterval(
            subFrom,
            subTo,
            2,
            lerp(from[2], to[2], rect.tMin()),
            lerp(from[2], to[2], rect.tMax()));
      }
      case "down" -> {
        setInterval(
            subFrom,
            subTo,
            0,
            lerp(from[0], to[0], rect.sMin()),
            lerp(from[0], to[0], rect.sMax()));
        setInterval(
            subFrom,
            subTo,
            2,
            lerp(to[2], from[2], rect.tMin()),
            lerp(to[2], from[2], rect.tMax()));
      }
      default -> throw new IllegalArgumentException("Unsupported model face: " + face);
    }
    return new double[][] {subFrom, subTo};
  }

  private static void setInterval(
      double[] from, double[] to, int axis, double first, double second) {
    from[axis] = Math.min(first, second);
    to[axis] = Math.max(first, second);
  }

  private static double lerp(double from, double to, double progress) {
    return from + (to - from) * progress;
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

  private static JsonArray uvForFace(String face, double[] from, double[] to) {
    return switch (face) {
      case "north", "south" -> numberArray(from[0], 16.0 - to[1], to[0], 16.0 - from[1]);
      case "east", "west" ->
          numberArray(16.0 - from[2], 16.0 - to[1], 16.0 - to[2], 16.0 - from[1]);
      case "up", "down" -> numberArray(16.0 - from[0], 16.0 - from[2], 16.0 - to[0], 16.0 - to[2]);
      default -> throw new IllegalArgumentException("Unsupported model face: " + face);
    };
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

  private record FaceCoverage(boolean opaque, boolean transparent, List<FaceRect> rects) {
    static FaceCoverage allOpaque() {
      return new FaceCoverage(true, false, List.of());
    }

    static FaceCoverage allTransparent() {
      return new FaceCoverage(false, true, List.of());
    }

    static FaceCoverage fromAlpha(AlphaImage alpha, double[] uv, int rotation) {
      double minU = Math.min(uv[0], uv[2]);
      double maxU = Math.max(uv[0], uv[2]);
      double minV = Math.min(uv[1], uv[3]);
      double maxV = Math.max(uv[1], uv[3]);
      int xStart = clamp((int) Math.floor(minU / 16.0 * alpha.width()), 0, alpha.width());
      int xEnd = clamp((int) Math.ceil(maxU / 16.0 * alpha.width()), 0, alpha.width());
      int yStart = clamp((int) Math.floor(minV / 16.0 * alpha.height()), 0, alpha.height());
      int yEnd = clamp((int) Math.ceil(maxV / 16.0 * alpha.height()), 0, alpha.height());
      if (xStart >= xEnd || yStart >= yEnd) {
        return allOpaque();
      }

      int width = xEnd - xStart;
      int height = yEnd - yStart;
      boolean[][] visible = new boolean[height][width];
      int visibleCount = 0;
      for (int y = yStart; y < yEnd; y++) {
        for (int x = xStart; x < xEnd; x++) {
          boolean isVisible = alpha.alphaAt(x, y) > 0;
          visible[y - yStart][x - xStart] = isVisible;
          if (isVisible) {
            visibleCount++;
          }
        }
      }

      int total = width * height;
      if (visibleCount == 0) {
        return allTransparent();
      }
      if (visibleCount == total) {
        return allOpaque();
      }

      boolean[][] used = new boolean[height][width];
      List<FaceRect> rects = new ArrayList<>();
      for (int row = 0; row < height; row++) {
        for (int col = 0; col < width; col++) {
          if (!visible[row][col] || used[row][col]) {
            continue;
          }
          int rectWidth = 1;
          while (col + rectWidth < width
              && visible[row][col + rectWidth]
              && !used[row][col + rectWidth]) {
            rectWidth++;
          }
          int rectHeight = 1;
          boolean canGrow = true;
          while (row + rectHeight < height && canGrow) {
            for (int x = col; x < col + rectWidth; x++) {
              if (!visible[row + rectHeight][x] || used[row + rectHeight][x]) {
                canGrow = false;
                break;
              }
            }
            if (canGrow) {
              rectHeight++;
            }
          }
          for (int y = row; y < row + rectHeight; y++) {
            for (int x = col; x < col + rectWidth; x++) {
              used[y][x] = true;
            }
          }

          double rectMinU = Math.max(minU, (xStart + col) * 16.0 / alpha.width());
          double rectMaxU = Math.min(maxU, (xStart + col + rectWidth) * 16.0 / alpha.width());
          double rectMinV = Math.max(minV, (yStart + row) * 16.0 / alpha.height());
          double rectMaxV = Math.min(maxV, (yStart + row + rectHeight) * 16.0 / alpha.height());
          rects.add(FaceRect.fromUv(rectMinU, rectMinV, rectMaxU, rectMaxV, uv, rotation));
        }
      }
      return new FaceCoverage(false, false, rects);
    }
  }

  private record FaceRect(double sMin, double sMax, double tMin, double tMax) {
    static FaceRect fromUv(
        double minU, double minV, double maxU, double maxV, double[] uv, int rotation) {
      double[][] points =
          new double[][] {
            uvToFaceLocal(minU, minV, uv, rotation),
            uvToFaceLocal(maxU, minV, uv, rotation),
            uvToFaceLocal(minU, maxV, uv, rotation),
            uvToFaceLocal(maxU, maxV, uv, rotation)
          };
      double sMin = Double.POSITIVE_INFINITY;
      double sMax = Double.NEGATIVE_INFINITY;
      double tMin = Double.POSITIVE_INFINITY;
      double tMax = Double.NEGATIVE_INFINITY;
      for (double[] point : points) {
        sMin = Math.min(sMin, point[0]);
        sMax = Math.max(sMax, point[0]);
        tMin = Math.min(tMin, point[1]);
        tMax = Math.max(tMax, point[1]);
      }
      return new FaceRect(clampUnit(sMin), clampUnit(sMax), clampUnit(tMin), clampUnit(tMax));
    }

    private static double[] uvToFaceLocal(double u, double v, double[] uv, int rotation) {
      double width = uv[2] - uv[0];
      double height = uv[3] - uv[1];
      if (Math.abs(width) < COORDINATE_EPSILON || Math.abs(height) < COORDINATE_EPSILON) {
        throw new IllegalArgumentException("Model face uv has zero width or height.");
      }
      double a = (u - uv[0]) / width;
      double b = (v - uv[1]) / height;
      return switch (rotation) {
        case 0 -> new double[] {a, b};
        case 90 -> new double[] {1.0 - b, a};
        case 180 -> new double[] {1.0 - a, 1.0 - b};
        case 270 -> new double[] {b, 1.0 - a};
        default ->
            throw new IllegalArgumentException("Unsupported face texture rotation: " + rotation);
      };
    }
  }

  private record AlphaImage(BufferedImage image) {
    int width() {
      return image.getWidth();
    }

    int height() {
      return image.getHeight();
    }

    int alphaAt(int x, int y) {
      return (image.getRGB(x, y) >>> 24) & 0xFF;
    }
  }

  private static final class TextureAlphaIndex {
    private static final TextureAlphaIndex EMPTY = new TextureAlphaIndex(Map.of());

    private final Map<String, byte[]> entries;
    private final Map<String, AlphaImage> cache = new HashMap<>();

    private TextureAlphaIndex(Map<String, byte[]> entries) {
      this.entries = Objects.requireNonNull(entries, "entries");
    }

    private static TextureAlphaIndex empty() {
      return EMPTY;
    }

    private AlphaImage image(String texture) {
      TextureAssetRef ref = TextureAssetRef.parse(texture);
      if (ref == null) {
        return null;
      }
      String entry = "assets/" + ref.namespace() + "/textures/" + ref.path() + ".png";
      if (!entries.containsKey(entry)) {
        return null;
      }
      return cache.computeIfAbsent(entry, this::readImage);
    }

    private AlphaImage readImage(String entry) {
      try {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(entries.get(entry)));
        if (image == null) {
          throw new IllegalArgumentException("Unsupported PNG texture: " + entry);
        }
        return new AlphaImage(image);
      } catch (IOException e) {
        throw new IllegalArgumentException("Failed to read PNG texture: " + entry, e);
      }
    }
  }

  private record TextureAssetRef(String namespace, String path) {
    static TextureAssetRef parse(String texture) {
      if (texture == null || texture.isBlank() || texture.startsWith("#")) {
        return null;
      }
      String normalized = texture.trim();
      int separator = normalized.indexOf(':');
      if (separator < 0) {
        return new TextureAssetRef("minecraft", normalized);
      }
      String namespace = normalized.substring(0, separator);
      String path = normalized.substring(separator + 1);
      if (namespace.isBlank() || path.isBlank()) {
        return null;
      }
      return new TextureAssetRef(namespace, path);
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static double clampUnit(double value) {
    if (value < 0.0 && value > -COORDINATE_EPSILON) {
      return 0.0;
    }
    if (value > 1.0 && value < 1.0 + COORDINATE_EPSILON) {
      return 1.0;
    }
    return Math.max(0.0, Math.min(1.0, value));
  }

  record Transform(Quaternionf rotation) {
    Transform {
      rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
    }
  }

  private record VariantSource(String modelKey, JsonObject sourceModel, Transform transform) {}
}
