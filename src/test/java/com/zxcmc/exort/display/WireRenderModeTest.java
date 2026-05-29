package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WireRenderModeTest {
  @Test
  void defaultsToAutoForBlankOrUnknownValues() {
    assertEquals(WireRenderMode.AUTO, WireRenderMode.fromString(null));
    assertEquals(WireRenderMode.AUTO, WireRenderMode.fromString(""));
    assertEquals(WireRenderMode.AUTO, WireRenderMode.fromString("unexpected"));
  }

  @Test
  void acceptsExplicitModesCaseInsensitively() {
    assertEquals(WireRenderMode.AUTO, WireRenderMode.fromString("auto"));
    assertEquals(WireRenderMode.DETAILED, WireRenderMode.fromString("detailed"));
    assertEquals(WireRenderMode.COMPACT, WireRenderMode.fromString("compact"));
  }

  @Test
  void compactMasksHaveModelKeysAndRotations() {
    for (int mask = 1; mask < 64; mask++) {
      assertFalse(WireDisplayManager.compactModelKeyForMask(mask).isBlank(), "mask=" + mask);
      assertTrue(WireDisplayManager.hasCompactRotationForMask(mask), "mask=" + mask);
    }
  }

  @Test
  void compactModelKeysHavePackAssets() {
    Set<String> keys = new HashSet<>();
    for (int mask = 1; mask < 64; mask++) {
      keys.add(WireDisplayManager.compactModelKeyForMask(mask));
    }

    for (String key : keys) {
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
  void compactCrossModelsAvoidOverlappingFullBars() throws Exception {
    Set<String> keys = Set.of("ns", "nse", "udns", "unse", "dnsew", "udnsew");

    for (String key : keys) {
      String json =
          Files.readString(
              Path.of("src/main/resources/pack/assets/exort/models/wire", key + ".json"));
      assertFalse(json.contains("\"from\": [6, 6, 0], \"to\": [10, 10, 16]"), key);
      assertFalse(json.contains("\"from\": [6, 0, 6], \"to\": [10, 16, 10]"), key);
      assertFalse(json.contains("\"from\": [0, 6, 6], \"to\": [16, 10, 10]"), key);
    }
  }
}
