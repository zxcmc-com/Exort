package com.zxcmc.exort.infra.resourcepack.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcePackTranslationExportTest {
  @TempDir Path tempDir;

  @Test
  void exportGeneratesSlimClientLanguageJsonFromRuntimeYaml() throws Exception {
    PackExporter.Result result =
        PackExporter.exportPack(
            Path.of("src/main/resources"), "pack/", tempDir.toFile(), Logger.getLogger("test"));

    assertTrue(result.available());
    try (ZipFile zip = new ZipFile(result.outputFile())) {
      var entry = zip.getEntry("assets/exort/lang/en_us.json");
      assertTrue(entry != null, "missing generated en_us resource-pack language file");
      String json = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(json.contains("\"exort.item.storage\": \"Storage\""));
      assertTrue(json.contains("\"exort.item.storage_core\": \"Storage Core\""));
      assertTrue(json.contains("\"exort.tier.rare\": \"Rare\""));
      assertTrue(json.contains("\"exort.lore.storage.tier\": \"Tier: %1$s\""));
      assertTrue(json.contains("\"exort.lore.wireless_terminal.battery\": \"Battery: %1$s%%\""));
      assertFalse(json.contains("exort.message.no_permission"));
      assertFalse(json.contains("exort.gui.search.button"));
    }
  }
}
