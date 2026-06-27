package com.zxcmc.exort.integration.worldedit.fawe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaweExtentAccessTest {
  @TempDir Path tempDir;

  @Test
  void configHelperAddsMarkerExtentClass() throws Exception {
    Path config = tempDir.resolve("config.yml");
    Files.writeString(config, "extent:\n  allowed-plugins: []\n");

    FaweExtentAccess.ConfigResult result =
        FaweExtentAccess.allowMarkerExtentInConfig(config.toFile(), "example.MarkerExtent");

    assertTrue(result.fileFound());
    assertTrue(result.modified());
    assertTrue(result.saved());
    assertTrue(Files.readString(config).contains("example.MarkerExtent"));
  }

  @Test
  void configHelperLeavesExistingMarkerExtentClassUntouched() throws Exception {
    Path config = tempDir.resolve("config.yml");
    Files.writeString(config, "extent:\n  allowed-plugins:\n  - example.MarkerExtent\n");

    FaweExtentAccess.ConfigResult result =
        FaweExtentAccess.allowMarkerExtentInConfig(config.toFile(), "example.MarkerExtent");

    assertTrue(result.fileFound());
    assertFalse(result.modified());
    assertFalse(result.saved());
  }

  @Test
  void configHelperAddsOraxenExtentWithoutDroppingMarkerExtent() throws Exception {
    Path config = tempDir.resolve("config.yml");
    String marker = "com.zxcmc.exort.integration.worldedit.WorldEditBridge$MarkerExtent";
    String oraxen = "io.th0rgal.oraxen.compatibilities.provided.worldedit.WorldEditHandlers$1";
    Files.writeString(config, "extent:\n  allowed-plugins:\n  - " + marker + "\n");

    FaweExtentAccess.ConfigResult result =
        FaweExtentAccess.allowExtentInConfig(config.toFile(), oraxen);
    String updated = Files.readString(config);

    assertTrue(result.fileFound());
    assertTrue(result.modified());
    assertTrue(result.saved());
    assertTrue(updated.contains(marker));
    assertTrue(updated.contains(oraxen));
  }

  @Test
  void configHelperAddsNexoExtentWithoutDroppingMarkerOrOraxenExtents() throws Exception {
    Path config = tempDir.resolve("config.yml");
    String marker = "com.zxcmc.exort.integration.worldedit.WorldEditBridge$MarkerExtent";
    String oraxen = "io.th0rgal.oraxen.compatibilities.provided.worldedit.WorldEditHandlers$1";
    String nexo = "com.nexomc.nexo.compatibilities.worldedit.NexoWorldEditExtent";
    Files.writeString(
        config, "extent:\n  allowed-plugins:\n  - " + marker + "\n  - " + oraxen + "\n");

    FaweExtentAccess.ConfigResult result =
        FaweExtentAccess.allowExtentInConfig(config.toFile(), nexo);
    String updated = Files.readString(config);

    assertTrue(result.fileFound());
    assertTrue(result.modified());
    assertTrue(result.saved());
    assertTrue(updated.contains(marker));
    assertTrue(updated.contains(oraxen));
    assertTrue(updated.contains(nexo));
  }

  @Test
  void existingAllowedRuntimeAllowedResultStaysQuiet() {
    FaweExtentAccess.Result result =
        new FaweExtentAccess.Result(
            new FaweExtentAccess.ConfigResult("/tmp/fawe.yml", true, false, false, null),
            false,
            false,
            null,
            true,
            null);

    assertFalse(result.shouldLogInfo());
    assertFalse(result.shouldLogWarning());
    assertFalse(FaweExtentAccess.shouldLogWarning(result, "example.QuietExtent"));
  }

  @Test
  void addedConfigEntryLogsCompactInfo() {
    FaweExtentAccess.Result result =
        new FaweExtentAccess.Result(
            new FaweExtentAccess.ConfigResult("/tmp/fawe.yml", true, true, true, null),
            true,
            true,
            null,
            true,
            null);

    assertTrue(result.shouldLogInfo());
    assertFalse(result.shouldLogWarning());
    assertEquals(
        "[WorldEdit] Added FAWE marker extent to allowed-plugins: example.MarkerExtent",
        result.infoMessage("example.MarkerExtent"));
  }

  @Test
  void missingConfigFileLogsWarningReason() {
    FaweExtentAccess.Result result =
        new FaweExtentAccess.Result(
            new FaweExtentAccess.ConfigResult("/tmp/missing.yml", false, false, false, null),
            false,
            false,
            null,
            true,
            null);

    assertTrue(result.shouldLogWarning());
    assertFalse(result.shouldLogInfo());
    assertEquals(
        "[WorldEdit] FAWE marker extent is not fully allowed: config not found; "
            + "class=example.MarkerExtent; config=/tmp/missing.yml",
        result.warningMessage("example.MarkerExtent"));
  }

  @Test
  void runtimeNotAllowedLogsWarningReason() {
    FaweExtentAccess.Result result =
        new FaweExtentAccess.Result(
            new FaweExtentAccess.ConfigResult("/tmp/fawe.yml", true, false, false, null),
            false,
            false,
            null,
            false,
            null);

    assertTrue(result.shouldLogWarning());
    assertEquals(
        "[WorldEdit] FAWE Nexo extent is not fully allowed: runtime not allowed; "
            + "class=example.NexoExtent; config=/tmp/fawe.yml",
        result.warningMessage("Nexo", "example.NexoExtent"));
  }

  @Test
  void warningOnceIsKeyedByExtentClass() {
    FaweExtentAccess.Result result =
        new FaweExtentAccess.Result(
            new FaweExtentAccess.ConfigResult("/tmp/fawe.yml", true, false, false, null),
            false,
            false,
            null,
            false,
            null);
    String first = "example.WarningExtentA" + System.nanoTime();
    String second = "example.WarningExtentB" + System.nanoTime();

    assertTrue(FaweExtentAccess.shouldLogWarning(result, first));
    assertFalse(FaweExtentAccess.shouldLogWarning(result, first));
    assertTrue(FaweExtentAccess.shouldLogWarning(result, second));
  }
}
