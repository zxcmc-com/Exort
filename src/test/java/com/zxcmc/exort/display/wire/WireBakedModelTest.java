package com.zxcmc.exort.display.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WireBakedModelTest {
  @Test
  void compactModelKeysAreUniqueAndHavePackAssets() throws Exception {
    Set<String> keys = new HashSet<>();
    for (int mask = 0; mask < 64; mask++) {
      String key = WireDisplayManager.compactModelKeyForMask(mask);

      assertTrue(keys.add(key), "duplicate key " + key);
      assertTrue(key.equals("center") || key.matches("[udnsew]+"), key);
      assertTrue(Files.isRegularFile(itemPath(key)), "missing item model for " + key);
      assertTrue(Files.isRegularFile(modelPath(key)), "missing block model for " + key);
      assertEquals("exort:wire/" + key, itemModelId(key));
    }
  }

  @Test
  void compactBlockModelsUseResolvableBaseTextureAndValidElementBounds() throws Exception {
    for (int mask = 0; mask < 64; mask++) {
      String key = WireDisplayManager.compactModelKeyForMask(mask);
      JsonObject model = JsonParser.parseString(Files.readString(modelPath(key))).getAsJsonObject();

      assertEquals("exort:block/wire", model.getAsJsonObject("textures").get("base").getAsString());
      assertFalse(model.getAsJsonArray("elements").isEmpty(), key);
      for (var element : model.getAsJsonArray("elements")) {
        JsonObject object = element.getAsJsonObject();
        assertVectorInsideBlock(object.getAsJsonArray("from"), key);
        assertVectorInsideBlock(object.getAsJsonArray("to"), key);
        for (var face : object.getAsJsonObject("faces").entrySet()) {
          assertEquals("#base", face.getValue().getAsJsonObject().get("texture").getAsString());
        }
      }
    }
  }

  @Test
  void legacyConnectionAssetsAreAbsent() {
    assertFalse(Files.exists(itemPath("connection")));
    assertFalse(Files.exists(modelPath("connection")));
  }

  private static Path itemPath(String key) {
    return Path.of("src/main/resources/pack/assets/exort/items/wire", key + ".json");
  }

  private static Path modelPath(String key) {
    return Path.of("src/main/resources/pack/assets/exort/models/wire", key + ".json");
  }

  private static String itemModelId(String key) throws Exception {
    JsonObject root = JsonParser.parseString(Files.readString(itemPath(key))).getAsJsonObject();
    JsonObject model = root.getAsJsonObject("model");
    assertNotNull(model, key);
    return model.get("model").getAsString();
  }

  private static void assertVectorInsideBlock(JsonArray vector, String key) {
    assertEquals(3, vector.size(), key);
    for (int i = 0; i < vector.size(); i++) {
      double value = vector.get(i).getAsDouble();
      assertTrue(value >= 0.0 && value <= 16.0, key + " vector value " + value);
    }
  }
}
