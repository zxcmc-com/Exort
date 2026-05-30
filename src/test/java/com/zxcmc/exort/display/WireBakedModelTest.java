package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WireBakedModelTest {
  private static final String[] SUFFIX_FACES = {"u", "d", "n", "s", "e", "w"};
  private static final Element CENTER =
      new Element(
          new int[] {6, 6, 6},
          new int[] {10, 10, 10},
          List.of(
              face("north", 6, 6, 10, 10, 270),
              face("east", 6, 6, 10, 10),
              face("south", 10, 6, 6, 10, 90),
              face("west", 6, 6, 10, 10, 180),
              face("up", 6, 6, 10, 10, 90),
              face("down", 10, 6, 6, 10, 90)));
  private static final Element CONNECTION =
      new Element(
          new int[] {6, 6, 0},
          new int[] {10, 10, 6},
          List.of(
              face("east", 10, 6, 16, 10),
              face("west", 16, 6, 10, 10),
              face("up", 6, 0, 10, 6),
              face("down", 6, 10, 10, 16)));
  private static final List<Rotation> SOURCE_ROTATIONS = generateSourceRotations();
  private static final Map<String, Quaternionf> CONNECTION_ROTATIONS = connectionRotations();

  @Test
  void compactMasksUseExactSuffixKeys() {
    for (int mask = 1; mask < 64; mask++) {
      assertEquals(suffix(mask), WireDisplayManager.compactModelKeyForMask(mask), "mask=" + mask);
    }
  }

  @Test
  void compactModelKeysHavePackAssets() {
    for (int mask = 1; mask < 64; mask++) {
      String key = WireDisplayManager.compactModelKeyForMask(mask);
      assertTrue(
          Files.isRegularFile(
              Path.of("src/main/resources/pack/assets/exort/items/wire", key + ".json")),
          "missing item model for " + key);
      assertTrue(
          Files.isRegularFile(
              Path.of("src/main/resources/pack/assets/exort/models/wire", key + ".json")),
          "missing block model for " + key);
    }
  }

  @Test
  void legacyConnectionAssetsAreAbsent() {
    assertFalse(
        Files.exists(Path.of("src/main/resources/pack/assets/exort/items/wire/connection.json")));
    assertFalse(
        Files.exists(Path.of("src/main/resources/pack/assets/exort/models/wire/connection.json")));
  }

  @Test
  void compactAssetsMatchCanonicalBakedModels() throws Exception {
    for (int mask = 1; mask < 64; mask++) {
      String key = WireDisplayManager.compactModelKeyForMask(mask);
      assertEquals(
          itemJson(key),
          Files.readString(
              Path.of("src/main/resources/pack/assets/exort/items/wire", key + ".json")),
          "item model for " + key);
      assertEquals(
          modelJson(key),
          Files.readString(
              Path.of("src/main/resources/pack/assets/exort/models/wire", key + ".json")),
          "block model for " + key);
    }
  }

  private static String suffix(int mask) {
    StringBuilder sb = new StringBuilder(6);
    for (int i = 0; i < SUFFIX_FACES.length; i++) {
      if ((mask & (1 << i)) != 0) {
        sb.append(SUFFIX_FACES[i]);
      }
    }
    return sb.toString();
  }

  private static String itemJson(String key) {
    return "{\"model\":{\"type\":\"model\",\"model\":\"exort:wire/" + key + "\",\"tints\":[]}}\n";
  }

  private static String modelJson(String key) {
    StringBuilder sb =
        new StringBuilder()
            .append("{\n")
            .append("  \"format_version\": \"1.21.6\",\n")
            .append("  \"textures\": {\"base\": \"exort:wires/glass\"},\n")
            .append("  \"elements\": [\n")
            .append("    ")
            .append(elementJson(CENTER));
    for (String face : SUFFIX_FACES) {
      if (key.contains(face)) {
        sb.append(",\n    ").append(elementJson(bakedConnectionArm(face)));
      }
    }
    return sb.append("\n  ]\n}\n").toString();
  }

  private static String elementJson(Element element) {
    StringBuilder sb =
        new StringBuilder()
            .append("{\"from\": ")
            .append(array(element.from()))
            .append(", \"to\": ")
            .append(array(element.to()))
            .append(", \"faces\": {");
    for (int i = 0; i < element.faces().size(); i++) {
      Face face = element.faces().get(i);
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("\"")
          .append(face.name())
          .append("\": {\"uv\": ")
          .append(array(face.uv()))
          .append(", ");
      if (face.rotation() != 0) {
        sb.append("\"rotation\": ").append(face.rotation()).append(", ");
      }
      sb.append("\"texture\": \"#base\"}");
    }
    return sb.append("}}").toString();
  }

  private static String array(int[] values) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(values[i]);
    }
    return sb.append(']').toString();
  }

  private static Element bakedConnectionArm(String key) {
    Quaternionf rotation = CONNECTION_ROTATIONS.get(key);
    if (rotation == null) {
      throw new IllegalArgumentException("Unsupported wire face: " + key);
    }

    // WireDisplayManager applies Y+180 compensation, then the client renderer applies its own
    // Y+180, so baked arms use the source connection rotation directly in JSON.
    List<int[]> transformedCorners = new ArrayList<>();
    for (int x : new int[] {CONNECTION.from()[0], CONNECTION.to()[0]}) {
      for (int y : new int[] {CONNECTION.from()[1], CONNECTION.to()[1]}) {
        for (int z : new int[] {CONNECTION.from()[2], CONNECTION.to()[2]}) {
          transformedCorners.add(transform(rotation, new int[] {x, y, z}));
        }
      }
    }

    int[] from = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    int[] to = new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    for (int[] corner : transformedCorners) {
      for (int axis = 0; axis < 3; axis++) {
        from[axis] = Math.min(from[axis], corner[axis]);
        to[axis] = Math.max(to[axis], corner[axis]);
      }
    }

    List<Face> faces = new ArrayList<>();
    for (Face sourceFace : CONNECTION.faces()) {
      int[][] sourceVertices = verticesFor(sourceFace.name(), CONNECTION.from(), CONNECTION.to());
      int[][] transformedVertices = new int[4][];
      for (int i = 0; i < 4; i++) {
        transformedVertices[i] = transform(rotation, sourceVertices[i]);
      }

      String targetFaceName = rotateFace(rotation, sourceFace.name());
      int[][] targetVertices = verticesFor(targetFaceName, from, to);
      int[][] targetUvByVertex = new int[4][];
      for (int i = 0; i < 4; i++) {
        int targetIndex = indexOf(targetVertices, transformedVertices[i]);
        targetUvByVertex[targetIndex] = uvAt(sourceFace, i);
      }

      BakedUv bakedUv = solveUv(targetUvByVertex);
      faces.add(new Face(targetFaceName, bakedUv.uv(), bakedUv.rotation()));
    }
    return new Element(from, to, faces);
  }

  private static Face face(String name, int u1, int v1, int u2, int v2) {
    return face(name, u1, v1, u2, v2, 0);
  }

  private static Face face(String name, int u1, int v1, int u2, int v2, int rotation) {
    return new Face(name, new int[] {u1, v1, u2, v2}, rotation);
  }

  private static Map<String, Quaternionf> connectionRotations() {
    Map<String, Quaternionf> rotations = new LinkedHashMap<>();
    int baseMask = bit("n");
    for (String face : SUFFIX_FACES) {
      Rotation rotation = findRotation(baseMask, bit(face));
      if (rotation == null) {
        throw new IllegalStateException("No source connection rotation for " + face);
      }
      rotations.put(face, new Quaternionf(rotation.quat()));
    }
    return rotations;
  }

  private static List<Rotation> generateSourceRotations() {
    Quaternionf rx = new Quaternionf().rotateX((float) (Math.PI / 2.0));
    Quaternionf ry = new Quaternionf().rotateY((float) (Math.PI / 2.0));
    Quaternionf rz = new Quaternionf().rotateZ((float) (Math.PI / 2.0));

    Queue<Quaternionf> queue = new ArrayDeque<>();
    queue.add(new Quaternionf());

    Map<String, Rotation> unique = new LinkedHashMap<>();
    while (!queue.isEmpty() && unique.size() < 24) {
      Quaternionf current = queue.poll();
      Rotation rotation = Rotation.from(current);
      if (unique.containsKey(rotation.key())) {
        continue;
      }
      unique.put(rotation.key(), rotation);

      queue.add(new Quaternionf(current).mul(rx));
      queue.add(new Quaternionf(current).mul(ry));
      queue.add(new Quaternionf(current).mul(rz));
    }
    return List.copyOf(unique.values());
  }

  private static Rotation findRotation(int baseMask, int targetMask) {
    for (Rotation rotation : SOURCE_ROTATIONS) {
      if (rotation.applyMask(baseMask) == targetMask) {
        return rotation;
      }
    }
    return null;
  }

  private static int bit(String face) {
    return 1 << faceIndex(face);
  }

  private static int faceIndex(String face) {
    return switch (face) {
      case "u" -> 0;
      case "d" -> 1;
      case "n" -> 2;
      case "s" -> 3;
      case "e" -> 4;
      case "w" -> 5;
      default -> throw new IllegalArgumentException("Unsupported face: " + face);
    };
  }

  private static String suffixFace(String jsonFace) {
    return switch (jsonFace) {
      case "up" -> "u";
      case "down" -> "d";
      case "north" -> "n";
      case "south" -> "s";
      case "east" -> "e";
      case "west" -> "w";
      default -> throw new IllegalArgumentException("Unsupported face: " + jsonFace);
    };
  }

  private static String jsonFace(String suffixFace) {
    return switch (suffixFace) {
      case "u" -> "up";
      case "d" -> "down";
      case "n" -> "north";
      case "s" -> "south";
      case "e" -> "east";
      case "w" -> "west";
      default -> throw new IllegalArgumentException("Unsupported face: " + suffixFace);
    };
  }

  private static int[] transform(Quaternionf rotation, int[] point) {
    Vector3f vector = new Vector3f(point[0] - 8, point[1] - 8, point[2] - 8);
    rotation.transform(vector);
    return new int[] {Math.round(vector.x) + 8, Math.round(vector.y) + 8, Math.round(vector.z) + 8};
  }

  private static String rotateFace(Quaternionf rotation, String jsonFace) {
    Vector3f vector = faceVector(suffixFace(jsonFace));
    rotation.transform(vector);
    int x = Math.round(vector.x);
    int y = Math.round(vector.y);
    int z = Math.round(vector.z);
    if (x == 1 && y == 0 && z == 0) return "east";
    if (x == -1 && y == 0 && z == 0) return "west";
    if (x == 0 && y == 1 && z == 0) return "up";
    if (x == 0 && y == -1 && z == 0) return "down";
    if (x == 0 && y == 0 && z == 1) return "south";
    if (x == 0 && y == 0 && z == -1) return "north";
    throw new IllegalStateException("Rotation produced non-axis face vector: " + vector);
  }

  private static Vector3f faceVector(String suffixFace) {
    return switch (suffixFace) {
      case "e" -> new Vector3f(1, 0, 0);
      case "w" -> new Vector3f(-1, 0, 0);
      case "u" -> new Vector3f(0, 1, 0);
      case "d" -> new Vector3f(0, -1, 0);
      case "s" -> new Vector3f(0, 0, 1);
      case "n" -> new Vector3f(0, 0, -1);
      default -> throw new IllegalArgumentException("Unsupported face: " + suffixFace);
    };
  }

  private static int[][] verticesFor(String face, int[] from, int[] to) {
    return switch (face) {
      case "down" ->
          new int[][] {
            {from[0], from[1], to[2]},
            {from[0], from[1], from[2]},
            {to[0], from[1], from[2]},
            {to[0], from[1], to[2]}
          };
      case "up" ->
          new int[][] {
            {from[0], to[1], from[2]},
            {from[0], to[1], to[2]},
            {to[0], to[1], to[2]},
            {to[0], to[1], from[2]}
          };
      case "north" ->
          new int[][] {
            {to[0], to[1], from[2]},
            {to[0], from[1], from[2]},
            {from[0], from[1], from[2]},
            {from[0], to[1], from[2]}
          };
      case "south" ->
          new int[][] {
            {from[0], to[1], to[2]},
            {from[0], from[1], to[2]},
            {to[0], from[1], to[2]},
            {to[0], to[1], to[2]}
          };
      case "west" ->
          new int[][] {
            {from[0], to[1], from[2]},
            {from[0], from[1], from[2]},
            {from[0], from[1], to[2]},
            {from[0], to[1], to[2]}
          };
      case "east" ->
          new int[][] {
            {to[0], to[1], to[2]},
            {to[0], from[1], to[2]},
            {to[0], from[1], from[2]},
            {to[0], to[1], from[2]}
          };
      default -> throw new IllegalArgumentException("Unsupported face: " + face);
    };
  }

  private static int indexOf(int[][] vertices, int[] target) {
    for (int i = 0; i < vertices.length; i++) {
      if (Arrays.equals(vertices[i], target)) {
        return i;
      }
    }
    throw new IllegalStateException("Cannot find transformed vertex " + Arrays.toString(target));
  }

  private static int[] uvAt(Face face, int vertexIndex) {
    int rotatedIndex = (vertexIndex + face.rotation() / 90) % 4;
    int u = rotatedIndex == 0 || rotatedIndex == 1 ? face.uv()[0] : face.uv()[2];
    int v = rotatedIndex == 0 || rotatedIndex == 3 ? face.uv()[1] : face.uv()[3];
    return new int[] {u, v};
  }

  private static BakedUv solveUv(int[][] uvByVertex) {
    for (int rotation = 0; rotation < 4; rotation++) {
      int[] u1 = new int[2];
      int[] u2 = new int[2];
      int[] v1 = new int[2];
      int[] v2 = new int[2];
      int u1Count = 0;
      int u2Count = 0;
      int v1Count = 0;
      int v2Count = 0;
      for (int vertex = 0; vertex < 4; vertex++) {
        int rotatedVertex = (vertex + rotation) % 4;
        if (rotatedVertex == 0 || rotatedVertex == 1) {
          u1[u1Count++] = uvByVertex[vertex][0];
        } else {
          u2[u2Count++] = uvByVertex[vertex][0];
        }
        if (rotatedVertex == 0 || rotatedVertex == 3) {
          v1[v1Count++] = uvByVertex[vertex][1];
        } else {
          v2[v2Count++] = uvByVertex[vertex][1];
        }
      }
      if (sameValues(u1, u1Count)
          && sameValues(u2, u2Count)
          && sameValues(v1, v1Count)
          && sameValues(v2, v2Count)) {
        return new BakedUv(new int[] {u1[0], v1[0], u2[0], v2[0]}, rotation * 90);
      }
    }
    throw new IllegalStateException("Cannot encode UVs " + Arrays.deepToString(uvByVertex));
  }

  private static boolean sameValues(int[] values, int count) {
    Set<Integer> unique = new HashSet<>();
    for (int i = 0; i < count; i++) {
      unique.add(values[i]);
    }
    return unique.size() == 1;
  }

  private record Element(int[] from, int[] to, List<Face> faces) {}

  private record Face(String name, int[] uv, int rotation) {}

  private record BakedUv(int[] uv, int rotation) {}

  private record Rotation(String key, Quaternionf quat, int[] faceMap) {
    int applyMask(int mask) {
      int out = 0;
      for (int i = 0; i < SUFFIX_FACES.length; i++) {
        if ((mask & (1 << i)) == 0) {
          continue;
        }
        out |= 1 << faceMap[i];
      }
      return out;
    }

    static Rotation from(Quaternionf q) {
      int[] faceMap = new int[SUFFIX_FACES.length];
      StringBuilder key = new StringBuilder(12);
      for (int i = 0; i < SUFFIX_FACES.length; i++) {
        String dst = rotateFace(q, jsonFace(SUFFIX_FACES[i]));
        int dstIndex = faceIndex(suffixFace(dst));
        faceMap[i] = dstIndex;
        key.append(i).append("->").append(dstIndex).append(';');
      }
      return new Rotation(key.toString(), new Quaternionf(q), faceMap);
    }
  }
}
