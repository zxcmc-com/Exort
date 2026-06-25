package com.zxcmc.exort.integration.worldedit;

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
}
