package com.zxcmc.exort.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigUpdaterTest {
  @TempDir Path tempDir;

  @Test
  void malformedExistingConfigIsRejectedWithoutChangingOriginal() throws Exception {
    Path config = tempDir.resolve("config.yml");
    String original = "valid: true\nbroken: [\n";
    Files.writeString(config, original);

    assertThrows(
        Exception.class,
        () -> ConfigUpdater.mergeResource(config, resource("valid: false\nmissing: 3\n")));

    assertEquals(original, Files.readString(config));
  }

  @Test
  void missingBundledResourceIsRejectedWithoutChangingOriginal() throws Exception {
    Path config = tempDir.resolve("config.yml");
    String original = "valid: true\n";
    Files.writeString(config, original);

    assertThrows(Exception.class, () -> ConfigUpdater.mergeResource(config, null));

    assertEquals(original, Files.readString(config));
  }

  @Test
  void atomicWriteReplacesConfigAndRemovesTemporaryFile() throws Exception {
    Path config = tempDir.resolve("config.yml");
    Files.writeString(config, "old: true\n");

    ConfigUpdater.writeAtomically(config, "new: true\n");

    assertEquals("new: true\n", Files.readString(config));
    try (var files = Files.list(tempDir)) {
      assertEquals(List.of(config), files.toList());
    }
  }

  @Test
  void mergeResourcePreservesExistingValuesAndAddsDefaults() throws Exception {
    Path config = tempDir.resolve("config.yml");
    Files.writeString(config, "enabled: false\n");

    String merged = ConfigUpdater.mergeResource(config, resource("enabled: true\nlimit: 32\n"));

    YamlConfiguration parsed = new YamlConfiguration();
    parsed.loadFromString(merged);
    assertEquals(false, parsed.getBoolean("enabled"));
    assertEquals(32, parsed.getInt("limit"));
  }

  @Test
  void rendersListsAsYamlBlockValues() {
    StringBuilder out = new StringBuilder();

    ConfigUpdater.renderValue(out, 4, "tools", List.of("pickaxe", "axe"));

    assertEquals(
        String.join(System.lineSeparator(), "    tools:", "      - pickaxe", "      - axe")
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
    defaults.set("break.wire.tools", List.of("pickaxe", "axe", "sword"));
    defaults.set("break.wire.hardness", 2.0);
    YamlConfiguration user = new YamlConfiguration();
    user.set("break.wire.tools", List.of("shears"));
    user.set("break.wire.hardness", 6.0);
    List<String> defaultLines =
        List.of(
            "break:",
            "  wire:",
            "    hardness: 2.0",
            "    tools:",
            "      - pickaxe",
            "      - axe",
            "      - sword");

    String merged =
        ConfigUpdater.mergeLinesWithDefaults(defaults, user, List.of(), defaultLines, true);

    assertTrue(merged.contains("    hardness: 6.0"), merged);
    assertTrue(merged.contains("    tools:"), merged);
    assertTrue(merged.contains("      - shears"), merged);
    assertEquals(-1, merged.indexOf("      - pickaxe"), merged);
    assertEquals(-1, merged.indexOf("      - axe"), merged);
    assertEquals(-1, merged.indexOf("      - sword"), merged);
  }

  @Test
  void mergeDropsUnknownKeysInsteadOfPreservingRetiredComments() {
    YamlConfiguration defaults = new YamlConfiguration();
    defaults.set("updateCheck", true);
    YamlConfiguration user = new YamlConfiguration();
    user.set("updateCheck", false);
    user.set("resourceMode.wire.renderMode", "AUTO");
    List<String> defaultLines = List.of("updateCheck: true");

    String merged =
        ConfigUpdater.mergeLinesWithDefaults(defaults, user, List.of(), defaultLines, true);

    assertTrue(merged.contains("updateCheck: false"), merged);
    assertEquals(-1, merged.indexOf("renderMode"), merged);
    assertEquals(-1, merged.indexOf("Removed/unknown options"), merged);
  }

  @Test
  void mergeUsesDefaultScalarWhenUserHasOldNestedSection() {
    YamlConfiguration defaults = new YamlConfiguration();
    defaults.set("updateCheck", true);
    defaults.set("performance.worldEditBulk", true);
    YamlConfiguration user = new YamlConfiguration();
    user.set("updateCheck.enabled", false);
    user.set("performance.worldEditBulk.enabled", false);
    List<String> defaultLines =
        List.of("updateCheck: true", "performance:", "  worldEditBulk: true");

    String merged =
        ConfigUpdater.mergeLinesWithDefaults(defaults, user, List.of(), defaultLines, true);

    assertTrue(merged.contains("updateCheck: true"), merged);
    assertTrue(merged.contains("  worldEditBulk: true"), merged);
    assertEquals(-1, merged.indexOf("enabled: false"), merged);
    assertEquals(-1, merged.indexOf("Removed/unknown options"), merged);
  }

  @Test
  void mergeAddsMissingWireCarrierWithoutOverwritingWireLimits() {
    YamlConfiguration defaults = new YamlConfiguration();
    defaults.set("wire.carrier", "CHORUS_PLANT");
    defaults.set("wire.limit", 32);
    defaults.set("wire.hardCap", 64);
    YamlConfiguration user = new YamlConfiguration();
    user.set("wire.limit", 48);
    user.set("wire.hardCap", 96);
    List<String> defaultLines =
        List.of(
            "wire:",
            "  # CHORUS_PLANT | BARRIER.",
            "  carrier: CHORUS_PLANT",
            "  limit: 32",
            "  hardCap: 64");

    String merged =
        ConfigUpdater.mergeLinesWithDefaults(defaults, user, List.of(), defaultLines, true);

    assertTrue(merged.contains("  carrier: CHORUS_PLANT"), merged);
    assertTrue(merged.contains("  limit: 48"), merged);
    assertTrue(merged.contains("  hardCap: 96"), merged);
  }

  private static ByteArrayInputStream resource(String yaml) {
    return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
  }
}
