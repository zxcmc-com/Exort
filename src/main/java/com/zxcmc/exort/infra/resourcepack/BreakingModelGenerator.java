package com.zxcmc.exort.infra.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zxcmc.exort.display.DisplayRotation;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
  static final int SCREEN_DESTROY_VISIBLE_ALPHA = 220;

  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  private static final String MODELS_ROOT = "assets/exort/models/";
  private static final String ITEMS_ROOT = "assets/exort/items/";
  private static final String BREAKING_ROOT = "breaking/";
  private static final String GENERATED_BREAKING_ROOT = BREAKING_ROOT + "generated/";
  private static final String SCREEN_TEXTURE_ROOT = "exort:display/";
  private static final int BASE_DESTROY_TEXTURE_SIZE = 16;
  private static final int BREAKING_TEXTURE_SUPERSAMPLE = 2;
  private static final int PRESERVE_DESTROY_VISIBLE_ALPHA = -1;
  private static final int MAX_DESTROY_TEXTURE_SIZE =
      BASE_DESTROY_TEXTURE_SIZE * BREAKING_TEXTURE_SUPERSAMPLE;
  private static final double BLOCK_CENTER = 8.0;
  private static final double COORDINATE_EPSILON = 0.000001;

  private BreakingModelGenerator() {}

  static int addGeneratedEntries(Logger logger, Map<String, byte[]> entries) {
    if (!hasBreakingSourceModels(entries)) {
      logger.log(Level.FINE, "No Exort source models found; breaking overlay generation skipped.");
      return 0;
    }
    TextureImageIndex textureImageIndex = new TextureImageIndex(entries);
    BreakingTextureRegistry breakingTextures =
        new BreakingTextureRegistry(entries, textureImageIndex, true);
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
                        variant.sourceModel(), stage, variant.transform(), breakingTextures))
                .getBytes(StandardCharsets.UTF_8));
      }
    }
    added += breakingTextures.addedCount();
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
    return generateModel(sourceModel, stage, transform, BreakingTextureRegistry.empty());
  }

  static JsonObject generateModel(
      JsonObject sourceModel, int stage, Transform transform, Map<String, byte[]> entries) {
    TextureImageIndex textureImageIndex = new TextureImageIndex(entries);
    return generateModel(
        sourceModel,
        stage,
        transform,
        new BreakingTextureRegistry(entries, textureImageIndex, false));
  }

  private static JsonObject generateModel(
      JsonObject sourceModel,
      int stage,
      Transform transform,
      BreakingTextureRegistry breakingTextures) {
    if (stage < 0 || stage >= STAGE_COUNT) {
      throw new IllegalArgumentException("Unsupported breaking stage: " + stage);
    }
    Objects.requireNonNull(sourceModel, "sourceModel");
    Objects.requireNonNull(transform, "transform");
    Objects.requireNonNull(breakingTextures, "breakingTextures");

    JsonObject root = new JsonObject();
    root.addProperty("format_version", "1.21.6");
    root.addProperty("credit", "phantomfighterxx");
    root.addProperty("ambientocclusion", false);

    ModelGenerationContext context = new ModelGenerationContext(stage, breakingTextures);
    root.add("textures", context.textures());

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
          transformElement(sourceElement.getAsJsonObject(), sourceTextures, transform, context)) {
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
      ModelGenerationContext context) {
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
        FaceAnalysis analysis =
            faceAnalysis(entry.getKey(), sourceFace, sourceTextures, sourceFrom, sourceTo, context);
        FaceCoverage coverage = analysis.coverage();
        String textureRef =
            context.textureReference(
                analysis.textureSize(),
                analysis.screen() ? SCREEN_DESTROY_VISIBLE_ALPHA : PRESERVE_DESTROY_VISIBLE_ALPHA);
        if (coverage.transparent()) {
          continue;
        }
        if (coverage.opaque()) {
          JsonArray uv =
              analysis.densityAligned()
                  ? densityAlignedUv(analysis)
                  : uvForFace(targetFace, from, to);
          if (uv == null) {
            continue;
          }
          baseFaces.add(targetFace, faceJson(uv, textureRef));
          continue;
        }
        int rectIndex = 0;
        for (FaceRect rect : coverage.rects()) {
          double[][] sourceSubBounds = subBoundsForFace(entry.getKey(), sourceFrom, sourceTo, rect);
          double[][] transformedSubBounds =
              transformBounds(sourceSubBounds[0], sourceSubBounds[1], transform.rotation());
          JsonArray uv =
              analysis.densityAligned()
                  ? densityAlignedUv(analysis, rect)
                  : uvForFace(targetFace, transformedSubBounds[0], transformedSubBounds[1]);
          if (uv == null) {
            continue;
          }
          JsonObject faces = new JsonObject();
          faces.add(targetFace, faceJson(uv, textureRef));
          targetElements.add(
              targetElement(
                  source,
                  transformedSubBounds[0],
                  transformedSubBounds[1],
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

  private static FaceAnalysis faceAnalysis(
      String face,
      JsonObject sourceFace,
      JsonObject sourceTextures,
      double[] from,
      double[] to,
      ModelGenerationContext context) {
    int rotation = faceTextureRotation(sourceFace);
    if (!sourceFace.has("texture") || !sourceFace.has("uv")) {
      return new FaceAnalysis(
          FaceCoverage.allOpaque(), TextureSize.base(), null, rotation, false, false);
    }
    String texture = resolveTexture(sourceFace.get("texture").getAsString(), sourceTextures);
    TextureImage image = context.textureImage(texture);
    if (image == null) {
      return new FaceAnalysis(
          FaceCoverage.allOpaque(), TextureSize.base(), null, rotation, false, false);
    }
    double[] uv = readUv(sourceFace);
    boolean screen = isScreenTexture(texture);
    double[] sourcePixels = sourcePixelSize(image.width(), image.height(), uv, rotation);
    return new FaceAnalysis(
        FaceCoverage.fromAlpha(image, uv, rotation),
        densityTextureSize(face, from, to, image.width(), image.height(), uv, rotation),
        sourcePixels,
        rotation,
        screen,
        screen);
  }

  private static boolean isScreenTexture(String texture) {
    return texture != null && texture.startsWith(SCREEN_TEXTURE_ROOT);
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

  private static JsonObject faceJson(JsonArray uv, String textureRef) {
    JsonObject face = new JsonObject();
    face.add("uv", uv);
    face.addProperty("texture", textureRef);
    face.addProperty("tintindex", 0);
    return face;
  }

  private static JsonArray densityAlignedUv(FaceAnalysis analysis) {
    double[] uv = densityAlignedUvBounds(analysis.sourcePixels());
    if (uv == null) {
      return null;
    }
    return clampedNumberArray(uv[0], uv[1], uv[2], uv[3]);
  }

  private static JsonArray densityAlignedUv(FaceAnalysis analysis, FaceRect rect) {
    double[] uv = densityAlignedUvBounds(analysis.sourcePixels());
    if (uv == null) {
      return null;
    }
    double[][] points =
        new double[][] {
          faceLocalToUv(rect.sMin(), rect.tMin(), uv, analysis.rotation()),
          faceLocalToUv(rect.sMax(), rect.tMin(), uv, analysis.rotation()),
          faceLocalToUv(rect.sMin(), rect.tMax(), uv, analysis.rotation()),
          faceLocalToUv(rect.sMax(), rect.tMax(), uv, analysis.rotation())
        };
    double minU = Double.POSITIVE_INFINITY;
    double minV = Double.POSITIVE_INFINITY;
    double maxU = Double.NEGATIVE_INFINITY;
    double maxV = Double.NEGATIVE_INFINITY;
    for (double[] point : points) {
      minU = Math.min(minU, point[0]);
      minV = Math.min(minV, point[1]);
      maxU = Math.max(maxU, point[0]);
      maxV = Math.max(maxV, point[1]);
    }
    return clampedNumberArray(minU, minV, maxU, maxV);
  }

  private static double[] densityAlignedUvBounds(double[] sourcePixels) {
    if (sourcePixels == null || sourcePixels.length != 2) {
      return null;
    }
    double width = densityAlignedUvSpan(sourcePixels[0]);
    double height = densityAlignedUvSpan(sourcePixels[1]);
    return new double[] {
      8.0 - width / 2.0, 8.0 - height / 2.0, 8.0 + width / 2.0, 8.0 + height / 2.0
    };
  }

  private static double densityAlignedUvSpan(double sourcePixels) {
    if (sourcePixels <= COORDINATE_EPSILON) {
      throw new IllegalArgumentException("Model face uv has zero width or height.");
    }
    return Math.min(
        BASE_DESTROY_TEXTURE_SIZE, Math.max(1.0, sourcePixels / BREAKING_TEXTURE_SUPERSAMPLE));
  }

  private static double[] faceLocalToUv(double s, double t, double[] uv, int rotation) {
    double a;
    double b;
    switch (rotation) {
      case 0 -> {
        a = s;
        b = t;
      }
      case 90 -> {
        a = t;
        b = 1.0 - s;
      }
      case 180 -> {
        a = 1.0 - s;
        b = 1.0 - t;
      }
      case 270 -> {
        a = 1.0 - t;
        b = s;
      }
      default ->
          throw new IllegalArgumentException("Unsupported face texture rotation: " + rotation);
    }
    return new double[] {lerp(uv[0], uv[2], a), lerp(uv[1], uv[3], b)};
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
    // Preserve overhanging model geometry while shifting projected destroy UVs into the sprite.
    // This keeps thin connector sides covered without compressing the crack pixel scale.
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

  private static JsonArray clampedNumberArray(double... values) {
    double[] clamped = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      clamped[i] = Math.max(0.0, Math.min(16.0, values[i]));
    }
    return numberArray(clamped);
  }

  static TextureSize densityTextureSize(
      String face, double[] from, double[] to, int textureWidth, int textureHeight, double[] uv) {
    return densityTextureSize(face, from, to, textureWidth, textureHeight, uv, 0);
  }

  static TextureSize densityTextureSize(
      String face,
      double[] from,
      double[] to,
      int textureWidth,
      int textureHeight,
      double[] uv,
      int rotation) {
    double[] modelSize = faceModelSize(face, from, to);
    double[] sourcePixels = sourcePixelSize(textureWidth, textureHeight, uv, rotation);
    return new TextureSize(
        densityDimension(sourcePixels[0], modelSize[0]),
        densityDimension(sourcePixels[1], modelSize[1]));
  }

  private static int densityDimension(double sourcePixels, double modelUnits) {
    if (modelUnits <= COORDINATE_EPSILON) {
      throw new IllegalArgumentException("Model face has zero projected size.");
    }
    if (sourcePixels <= COORDINATE_EPSILON) {
      throw new IllegalArgumentException("Model face uv has zero width or height.");
    }
    int density =
        (int)
            Math.ceil(
                sourcePixels * BREAKING_TEXTURE_SUPERSAMPLE * BASE_DESTROY_TEXTURE_SIZE / modelUnits
                    - COORDINATE_EPSILON);
    return destroyTextureDimension(density, MAX_DESTROY_TEXTURE_SIZE);
  }

  private static int destroyTextureDimension(int required, int maximum) {
    int clamped = Math.min(maximum, Math.max(BASE_DESTROY_TEXTURE_SIZE, required));
    int dimension = BASE_DESTROY_TEXTURE_SIZE;
    while (dimension < clamped) {
      dimension *= BREAKING_TEXTURE_SUPERSAMPLE;
    }
    return Math.min(maximum, dimension);
  }

  private static double[] faceModelSize(String face, double[] from, double[] to) {
    return switch (face) {
      case "north", "south" -> new double[] {Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1])};
      case "east", "west" -> new double[] {Math.abs(to[2] - from[2]), Math.abs(to[1] - from[1])};
      case "up", "down" -> new double[] {Math.abs(to[0] - from[0]), Math.abs(to[2] - from[2])};
      default -> throw new IllegalArgumentException("Unsupported model face: " + face);
    };
  }

  private static double[] sourcePixelSize(
      int textureWidth, int textureHeight, double[] uv, int rotation) {
    double width = Math.abs(uv[2] - uv[0]) / BASE_DESTROY_TEXTURE_SIZE * textureWidth;
    double height = Math.abs(uv[3] - uv[1]) / BASE_DESTROY_TEXTURE_SIZE * textureHeight;
    return switch (rotation) {
      case 0, 180 -> new double[] {width, height};
      case 90, 270 -> new double[] {height, width};
      default ->
          throw new IllegalArgumentException("Unsupported face texture rotation: " + rotation);
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

  private record FaceAnalysis(
      FaceCoverage coverage,
      TextureSize textureSize,
      double[] sourcePixels,
      int rotation,
      boolean densityAligned,
      boolean screen) {}

  private record FaceCoverage(boolean opaque, boolean transparent, List<FaceRect> rects) {
    static FaceCoverage allOpaque() {
      return new FaceCoverage(true, false, List.of());
    }

    static FaceCoverage allTransparent() {
      return new FaceCoverage(false, true, List.of());
    }

    static FaceCoverage fromAlpha(TextureImage alpha, double[] uv, int rotation) {
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

  record TextureSize(int width, int height) {
    TextureSize {
      if (width <= 0 || height <= 0) {
        throw new IllegalArgumentException("Breaking texture size must be positive.");
      }
    }

    static TextureSize base() {
      return new TextureSize(BASE_DESTROY_TEXTURE_SIZE, BASE_DESTROY_TEXTURE_SIZE);
    }
  }

  private record TextureImage(BufferedImage image) {
    int width() {
      return image.getWidth();
    }

    int height() {
      return image.getHeight();
    }

    int alphaAt(int x, int y) {
      return (image.getRGB(x, y) >>> 24) & 0xFF;
    }

    int argbAt(int x, int y) {
      if (isGrayAlpha()) {
        int[] pixel = image.getRaster().getPixel(x, y, (int[]) null);
        int gray = pixel[0] & 0xFF;
        int alpha = pixel[1] & 0xFF;
        return (alpha << 24) | (gray << 16) | (gray << 8) | gray;
      }
      return image.getRGB(x, y);
    }

    private boolean isGrayAlpha() {
      return image.getRaster().getNumBands() == 2
          && image.getColorModel().hasAlpha()
          && image.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY;
    }
  }

  private static final class TextureImageIndex {
    private static final TextureImageIndex EMPTY = new TextureImageIndex(Map.of());

    private final Map<String, byte[]> entries;
    private final Map<String, TextureImage> cache = new HashMap<>();

    private TextureImageIndex(Map<String, byte[]> entries) {
      this.entries = Objects.requireNonNull(entries, "entries");
    }

    private static TextureImageIndex empty() {
      return EMPTY;
    }

    private TextureImage image(String texture) {
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

    private TextureImage readImage(String entry) {
      try {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(entries.get(entry)));
        if (image == null) {
          throw new IllegalArgumentException("Unsupported PNG texture: " + entry);
        }
        return new TextureImage(image);
      } catch (IOException e) {
        throw new IllegalArgumentException("Failed to read PNG texture: " + entry, e);
      }
    }
  }

  private static final class ModelGenerationContext {
    private final int stage;
    private final BreakingTextureRegistry breakingTextures;
    private final JsonObject textures = new JsonObject();
    private final Map<String, String> textureSlots = new HashMap<>();
    private int nextTextureSlot = 1;

    private ModelGenerationContext(int stage, BreakingTextureRegistry breakingTextures) {
      this.stage = stage;
      this.breakingTextures = Objects.requireNonNull(breakingTextures, "breakingTextures");
      String baseTexture =
          breakingTexture(stage, TextureSize.base(), PRESERVE_DESTROY_VISIBLE_ALPHA);
      textures.addProperty("0", baseTexture);
      textures.addProperty("particle", baseTexture);
    }

    private JsonObject textures() {
      return textures;
    }

    private TextureImage textureImage(String texture) {
      return breakingTextures.textureImage(texture);
    }

    private String textureReference(TextureSize size, int visibleAlphaOverride) {
      String texture = breakingTextures.textureFor(stage, size, visibleAlphaOverride);
      if (texture.equals(
          breakingTexture(stage, TextureSize.base(), PRESERVE_DESTROY_VISIBLE_ALPHA))) {
        return "#0";
      }
      return "#" + textureSlots.computeIfAbsent(texture, this::addTextureSlot);
    }

    private String addTextureSlot(String texture) {
      String slot = Integer.toString(nextTextureSlot++);
      textures.addProperty(slot, texture);
      return slot;
    }
  }

  private static final class BreakingTextureRegistry {
    private static final BreakingTextureRegistry EMPTY =
        new BreakingTextureRegistry(Map.of(), TextureImageIndex.empty(), false);

    private final Map<String, byte[]> entries;
    private final TextureImageIndex textureImageIndex;
    private final boolean writeGeneratedTextures;
    private int addedCount;

    private BreakingTextureRegistry(
        Map<String, byte[]> entries,
        TextureImageIndex textureImageIndex,
        boolean writeGeneratedTextures) {
      this.entries = Objects.requireNonNull(entries, "entries");
      this.textureImageIndex = Objects.requireNonNull(textureImageIndex, "textureImageIndex");
      this.writeGeneratedTextures = writeGeneratedTextures;
    }

    private static BreakingTextureRegistry empty() {
      return EMPTY;
    }

    private int addedCount() {
      return addedCount;
    }

    private TextureImage textureImage(String texture) {
      return textureImageIndex.image(texture);
    }

    private String textureFor(int stage, TextureSize size, int visibleAlphaOverride) {
      String texture = breakingTexture(stage, size, visibleAlphaOverride);
      if (writeGeneratedTextures
          && (!size.equals(TextureSize.base())
              || visibleAlphaOverride != PRESERVE_DESTROY_VISIBLE_ALPHA)) {
        addGeneratedTexture(stage, size, visibleAlphaOverride);
      }
      return texture;
    }

    private void addGeneratedTexture(int stage, TextureSize size, int visibleAlphaOverride) {
      String entry = breakingTextureEntry(stage, size, visibleAlphaOverride);
      if (entries.containsKey(entry)) {
        return;
      }
      TextureImage source =
          textureImageIndex.image(
              breakingTexture(stage, TextureSize.base(), PRESERVE_DESTROY_VISIBLE_ALPHA));
      if (source == null) {
        throw new IllegalArgumentException(
            "Missing breaking destroy stage texture: "
                + breakingTexture(stage, TextureSize.base(), PRESERVE_DESTROY_VISIBLE_ALPHA));
      }
      entries.put(entry, densityPng(source, size, visibleAlphaOverride));
      addedCount++;
    }

    private static byte[] densityPng(
        TextureImage source, TextureSize size, int visibleAlphaOverride) {
      BufferedImage target =
          new BufferedImage(size.width(), size.height(), BufferedImage.TYPE_INT_ARGB);
      int background = backgroundPixel(source);
      for (int y = 0; y < target.getHeight(); y++) {
        for (int x = 0; x < target.getWidth(); x++) {
          target.setRGB(x, y, background);
        }
      }
      for (int y = 0; y < target.getHeight(); y++) {
        int sourceY = y * source.height() / target.getHeight();
        for (int x = 0; x < target.getWidth(); x++) {
          int sourceX = x * source.width() / target.getWidth();
          int argb = source.argbAt(sourceX, sourceY);
          if (((argb >>> 24) & 0xFF) <= 1) {
            continue;
          }
          if (visibleAlphaOverride != PRESERVE_DESTROY_VISIBLE_ALPHA) {
            argb = (argb & 0x00FFFFFF) | (visibleAlphaOverride << 24);
          }
          target.setRGB(x, y, argb);
        }
      }
      try {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(target, "png", out);
        return out.toByteArray();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to encode generated breaking texture.", e);
      }
    }

    private static int backgroundPixel(TextureImage source) {
      int best = source.argbAt(0, 0);
      int bestAlpha = (best >>> 24) & 0xFF;
      for (int y = 0; y < source.height(); y++) {
        for (int x = 0; x < source.width(); x++) {
          int argb = source.argbAt(x, y);
          int alpha = (argb >>> 24) & 0xFF;
          if (alpha < bestAlpha) {
            best = argb;
            bestAlpha = alpha;
          }
        }
      }
      return best;
    }
  }

  private static String breakingTexture(int stage, TextureSize size, int visibleAlphaOverride) {
    if (size.equals(TextureSize.base()) && visibleAlphaOverride == PRESERVE_DESTROY_VISIBLE_ALPHA) {
      return "exort:" + BREAKING_ROOT + "destroy_stage_" + stage;
    }
    return "exort:"
        + GENERATED_BREAKING_ROOT
        + "destroy_stage_"
        + stage
        + "_"
        + size.width()
        + "x"
        + size.height()
        + alphaSuffix(visibleAlphaOverride);
  }

  private static String breakingTextureEntry(
      int stage, TextureSize size, int visibleAlphaOverride) {
    return "assets/exort/textures/"
        + GENERATED_BREAKING_ROOT
        + "destroy_stage_"
        + stage
        + "_"
        + size.width()
        + "x"
        + size.height()
        + alphaSuffix(visibleAlphaOverride)
        + ".png";
  }

  private static String alphaSuffix(int visibleAlphaOverride) {
    return visibleAlphaOverride == PRESERVE_DESTROY_VISIBLE_ALPHA
        ? ""
        : "_a" + visibleAlphaOverride;
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
