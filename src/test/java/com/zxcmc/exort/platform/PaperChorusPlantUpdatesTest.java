package com.zxcmc.exort.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperChorusPlantUpdatesTest {
  @TempDir Path tempDir;

  @AfterEach
  void resetPaperRuntime() {
    io.papermc.paper.configuration.GlobalConfiguration.reset();
  }

  @Test
  void readsDisabledFlagFromPaperGlobalConfig() throws IOException {
    Path config = writePaperConfig("block-updates:\n  disable-chorus-plant-updates: true\n");

    PaperChorusPlantUpdates.Status status = PaperChorusPlantUpdates.read(tempDir.toFile());

    assertEquals(PaperChorusPlantUpdates.State.PRESENT, status.state());
    assertEquals(config.toFile(), status.file());
    assertTrue(status.disabled());
  }

  @Test
  void missingPaperGlobalConfigIsReportedAndNotCreated() {
    PaperChorusPlantUpdates.FixResult result = PaperChorusPlantUpdates.disable(tempDir.toFile());

    assertEquals(PaperChorusPlantUpdates.State.MISSING, result.state());
    assertFalse(Files.exists(tempDir.resolve(PaperChorusPlantUpdates.CONFIG_RELATIVE_PATH)));
  }

  @Test
  void disablesChorusUpdatesWithoutDroppingSiblingSettings() throws IOException {
    Path config =
        writePaperConfig(
            """
            _version: 31
            block-updates:
              disable-chorus-plant-updates: false # used by Exort wires
              disable-mushroom-block-updates: false
              disable-noteblock-updates: false
            chunk-system:
              io-threads: -1
            """);

    PaperChorusPlantUpdates.FixResult result = PaperChorusPlantUpdates.disable(tempDir.toFile());
    String updated = Files.readString(config);

    assertEquals(PaperChorusPlantUpdates.State.PRESENT, result.state());
    assertFalse(result.previousDisabled());
    assertTrue(result.changed());
    assertTrue(result.restartRequired());
    assertTrue(updated.contains("disable-chorus-plant-updates: true # used by Exort wires"));
    assertTrue(updated.contains("disable-mushroom-block-updates: false"));
    assertTrue(updated.contains("chunk-system:"));
  }

  @Test
  void disableNoopsWhenChorusUpdatesAreAlreadyDisabled() throws IOException {
    Path config = writePaperConfig("block-updates:\n  disable-chorus-plant-updates: true\n");
    String before = Files.readString(config);

    PaperChorusPlantUpdates.FixResult result = PaperChorusPlantUpdates.disable(tempDir.toFile());

    assertEquals(PaperChorusPlantUpdates.State.PRESENT, result.state());
    assertTrue(result.previousDisabled());
    assertFalse(result.changed());
    assertFalse(result.restartRequired());
    assertEquals(before, Files.readString(config));
  }

  @Test
  void readUsesActivePaperRuntimeWhenAvailable() throws IOException {
    writePaperConfig("block-updates:\n  disable-chorus-plant-updates: true\n");
    io.papermc.paper.configuration.GlobalConfiguration.setRuntimeDisabled(false);

    PaperChorusPlantUpdates.Status status = PaperChorusPlantUpdates.read(tempDir.toFile());

    assertEquals(PaperChorusPlantUpdates.State.PRESENT, status.state());
    assertFalse(status.disabled());
  }

  @Test
  void disableRequiresRestartWhenConfigIsTrueButRuntimeIsStillEnabled() throws IOException {
    Path config = writePaperConfig("block-updates:\n  disable-chorus-plant-updates: true\n");
    String before = Files.readString(config);
    io.papermc.paper.configuration.GlobalConfiguration.setRuntimeDisabled(false);

    PaperChorusPlantUpdates.FixResult result = PaperChorusPlantUpdates.disable(tempDir.toFile());

    assertEquals(PaperChorusPlantUpdates.State.PRESENT, result.state());
    assertFalse(result.previousDisabled());
    assertFalse(result.changed());
    assertTrue(result.restartRequired());
    assertEquals(before, Files.readString(config));
  }

  @Test
  void disableWritesConfigWithoutRestartWhenRuntimeIsAlreadyDisabled() throws IOException {
    Path config = writePaperConfig("block-updates:\n  disable-chorus-plant-updates: false\n");
    io.papermc.paper.configuration.GlobalConfiguration.setRuntimeDisabled(true);

    PaperChorusPlantUpdates.FixResult result = PaperChorusPlantUpdates.disable(tempDir.toFile());

    assertEquals(PaperChorusPlantUpdates.State.PRESENT, result.state());
    assertTrue(result.previousDisabled());
    assertTrue(result.changed());
    assertFalse(result.restartRequired());
    assertTrue(Files.readString(config).contains("disable-chorus-plant-updates: true"));
  }

  @Test
  void addsMissingKeyInsideExistingBlockUpdatesSection() throws IOException {
    Path config =
        writePaperConfig(
            """
            block-updates:
              disable-mushroom-block-updates: false
            """);

    PaperChorusPlantUpdates.FixResult result = PaperChorusPlantUpdates.disable(tempDir.toFile());
    String updated = Files.readString(config);

    assertEquals(PaperChorusPlantUpdates.State.PRESENT, result.state());
    assertTrue(result.changed());
    assertTrue(result.restartRequired());
    assertTrue(updated.contains("  disable-chorus-plant-updates: true\n"));
    assertTrue(updated.contains("  disable-mushroom-block-updates: false"));
  }

  @Test
  void accessDeniedReasonDoesNotRepeatConfigPath() throws IOException {
    Path config = writePaperConfig("block-updates:\n  disable-chorus-plant-updates: false\n");

    String reason =
        PaperChorusPlantUpdates.errorReason(
            new AccessDeniedException(config.toString()), config.toFile());
    PaperChorusPlantUpdates.FixResult result =
        PaperChorusPlantUpdates.FixResult.error(config.toFile(), true, reason);

    assertEquals("access denied", reason);
    assertFalse(reason.contains(config.toString()));
    assertTrue(result.accessDenied());
  }

  private Path writePaperConfig(String content) throws IOException {
    Path config = tempDir.resolve(PaperChorusPlantUpdates.CONFIG_RELATIVE_PATH);
    Files.createDirectories(config.getParent());
    Files.writeString(config, content);
    return config;
  }
}
