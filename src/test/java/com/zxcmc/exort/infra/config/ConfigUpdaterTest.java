package com.zxcmc.exort.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigUpdaterTest {
  @Test
  void rendersListsAsYamlBlockValues() {
    StringBuilder out = new StringBuilder();

    ConfigUpdater.renderValue(out, 4, "entityThresholds", List.of(160, 320, 640));

    assertEquals(
        String.join(
                System.lineSeparator(),
                "    entityThresholds:",
                "      - 160",
                "      - 320",
                "      - 640")
            + System.lineSeparator(),
        out.toString());
  }

  @Test
  void rendersScalarsOnOneLine() {
    StringBuilder out = new StringBuilder();

    ConfigUpdater.renderValue(out, 2, "enabled", true);

    assertEquals("  enabled: true" + System.lineSeparator(), out.toString());
  }

  @Test
  void mergePreservesUserListValuesFromBlockDefaults() {
    YamlConfiguration defaults = new YamlConfiguration();
    defaults.set(
        "performance.displayCulling.adaptiveViewRange.roleRanges.wire",
        List.of(0.5, 0.35, 0.25, 0.12));
    defaults.set("performance.displayCulling.adaptiveViewRange.enabled", true);
    YamlConfiguration user = new YamlConfiguration();
    user.set("performance.displayCulling.adaptiveViewRange.roleRanges.wire", List.of(0.5, 0.25));
    user.set("performance.displayCulling.adaptiveViewRange.enabled", false);
    List<String> defaultLines =
        List.of(
            "performance:",
            "  displayCulling:",
            "    adaptiveViewRange:",
            "      roleRanges:",
            "        wire:",
            "          - 0.5",
            "          - 0.35",
            "          - 0.25",
            "          - 0.12",
            "      enabled: true");

    String merged =
        ConfigUpdater.mergeLinesWithDefaults(defaults, user, List.of(), defaultLines, true);

    assertTrue(merged.contains("        wire:"), merged);
    assertTrue(merged.contains("          - 0.5"), merged);
    assertTrue(merged.contains("          - 0.25"), merged);
    assertTrue(merged.contains("      enabled: false"), merged);
    assertEquals(-1, merged.indexOf("          - 0.35"), merged);
    assertEquals(-1, merged.indexOf("          - 0.12"), merged);
  }

  @Test
  void mergeDropsRetiredClientCullingPlayersKey() {
    YamlConfiguration defaults = new YamlConfiguration();
    defaults.set("performance.displayCulling.clientCullingBypass.enabled", true);
    YamlConfiguration user = new YamlConfiguration();
    user.set("performance.displayCulling.clientCullingBypass.enabled", true);
    user.set(
        "performance.displayCulling.clientCullingBypass.players",
        List.of("00000000-0000-0000-0000-000000000123"));
    List<String> defaultLines =
        List.of(
            "performance:", "  displayCulling:", "    clientCullingBypass:", "      enabled: true");

    String merged =
        ConfigUpdater.mergeLinesWithDefaults(defaults, user, List.of(), defaultLines, true);

    assertEquals(-1, merged.indexOf("clientCullingBypass.players"), merged);
    assertEquals(-1, merged.indexOf("00000000-0000-0000-0000-000000000123"), merged);
  }
}
