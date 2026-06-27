package com.zxcmc.exort.integration.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcePackProviderBridgeTest {
  @TempDir Path tempDir;

  @Test
  void detectsProviderByTopLevelJarMetadataOnly() throws IOException {
    writeZip(
        tempDir.resolve("Nexo.jar"), Map.of("paper-plugin.yml", "name: 'Nexo'\nversion: '1.24'\n"));
    Files.createDirectories(tempDir.resolve(".disabled"));
    writeZip(
        tempDir.resolve(".disabled/ItemsAdder.jar"),
        Map.of("plugin.yml", "name: ItemsAdder\nversion: 4\n"));

    assertTrue(ResourcePackProviderBridge.isPluginJarInstalled(tempDir.toFile(), "Nexo"));
    assertFalse(ResourcePackProviderBridge.isPluginJarInstalled(tempDir.toFile(), "ItemsAdder"));

    writeZip(
        tempDir.resolve("ItemsAdder.jar"), Map.of("plugin.yml", "name: ItemsAdder\nversion: 4\n"));
    writeZip(
        tempDir.resolve("Oraxen.jar"), Map.of("plugin.yml", "name: Oraxen\nversion: 1.217.0\n"));

    assertTrue(ResourcePackProviderBridge.isPluginJarInstalled(tempDir.toFile(), "ItemsAdder"));
    assertTrue(ResourcePackProviderBridge.isPluginJarInstalled(tempDir.toFile(), "Oraxen"));
  }

  @Test
  void nexoHandoffSkipsUnchangedRawPack() throws IOException {
    Path source = tempDir.resolve("exort.raw.zip");
    writeZip(source, Map.of("assets/exort/items/storage/storage.json", "{}"));

    ResourcePackProviderBridge.HandoffResult first =
        ResourcePackProviderBridge.copyNexoPack(tempDir.toFile(), source.toFile());
    assertTrue(first.success());
    Path target = first.target().toPath();
    assertTrue(Files.isRegularFile(target));

    Files.setLastModifiedTime(target, FileTime.fromMillis(123456789L));
    FileTime preservedTime = Files.getLastModifiedTime(target);

    ResourcePackProviderBridge.HandoffResult second =
        ResourcePackProviderBridge.copyNexoPack(tempDir.toFile(), source.toFile());

    assertTrue(second.success());
    assertEquals(preservedTime, Files.getLastModifiedTime(target));
  }

  @Test
  void nexoApiHandoffRemovesOnlyExortExternalPackFallback() throws IOException {
    Path source = tempDir.resolve("exort.raw.zip");
    writeZip(source, Map.of("assets/exort/items/storage/storage.json", "{}"));
    Path exortFallback = tempDir.resolve("Nexo/pack/external_packs/zxcmc_exort.zip");
    Path otherPack = tempDir.resolve("Nexo/pack/external_packs/other.zip");
    Files.createDirectories(exortFallback.getParent());
    Files.writeString(exortFallback, "old-exort");
    Files.writeString(otherPack, "other");

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.prepareNexoApiHandoff(tempDir.toFile(), source.toFile());

    assertTrue(result.success());
    assertEquals(source, result.target().toPath());
    assertEquals("Nexo post-generate API: " + source, result.targetPath());
    assertFalse(Files.exists(exortFallback));
    assertTrue(Files.isRegularFile(otherPack));
  }

  @Test
  void oraxenHandoffCopiesRawPackToUploadsAndSkipsUnchangedPack() throws IOException {
    Path source = tempDir.resolve("exort.raw.zip");
    writeZip(source, Map.of("assets/exort/items/storage/storage.json", "{}"));

    ResourcePackProviderBridge.HandoffResult first =
        ResourcePackProviderBridge.copyOraxenPack(tempDir.toFile(), source.toFile());
    assertTrue(first.success());
    Path target = tempDir.resolve("Oraxen/pack/uploads/zxcmc_exort.zip");
    assertEquals(target, first.target().toPath());
    assertTrue(Files.isRegularFile(target));

    Files.setLastModifiedTime(target, FileTime.fromMillis(123456789L));
    FileTime preservedTime = Files.getLastModifiedTime(target);

    ResourcePackProviderBridge.HandoffResult second =
        ResourcePackProviderBridge.copyOraxenPack(tempDir.toFile(), source.toFile());

    assertTrue(second.success());
    assertEquals(preservedTime, Files.getLastModifiedTime(target));
  }

  @Test
  void itemsAdderHandoffSyncsOnlyAssetsIntoExortContentPack() throws IOException {
    Path source = tempDir.resolve("exort.raw.zip");
    writeZip(
        source,
        Map.of(
            "assets/exort/items/storage/storage.json", "{}",
            "assets/minecraft/blockstates/chorus_plant.json", "{}",
            "pack.mcmeta", "{\"pack\":{}}"));
    Path resourcepack = tempDir.resolve("ItemsAdder/contents/exort/resourcepack");
    Files.createDirectories(resourcepack.resolve("assets/exort/items/storage"));
    Files.writeString(resourcepack.resolve("assets/exort/items/storage/stale.json"), "{}");
    Files.writeString(resourcepack.resolve("pack.mcmeta"), "{\"stale\":true}");
    Path otherContent = tempDir.resolve("ItemsAdder/contents/other/resourcepack/assets/keep.txt");
    Files.createDirectories(otherContent.getParent());
    Files.writeString(otherContent, "keep");

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.syncItemsAdderPack(tempDir.toFile(), source.toFile());

    assertTrue(result.success());
    assertTrue(
        Files.isRegularFile(resourcepack.resolve("assets/exort/items/storage/storage.json")));
    assertTrue(
        Files.isRegularFile(
            resourcepack.resolve("assets/minecraft/blockstates/chorus_plant.json")));
    assertFalse(Files.exists(resourcepack.resolve("assets/exort/items/storage/stale.json")));
    assertFalse(Files.exists(resourcepack.resolve("pack.mcmeta")));
    assertTrue(Files.isRegularFile(otherContent));
  }

  @Test
  void itemsAdderHandoffRewritesModelTexturesToItemAtlasAliases() throws IOException {
    Path source = tempDir.resolve("exort.raw.zip");
    writeZip(
        source,
        Map.of(
            "assets/exort/models/storage/storage.json",
            """
            {
              "textures": {
                "0": "exort:block/storage",
                "particle": "exort:block/storage"
              },
              "outside_textures": "exort:block/storage"
            }
            """,
            "assets/exort/textures/block/storage.png",
            "png",
            "assets/minecraft/atlases/blocks.json",
            "{\"sources\":[]}"));

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.syncItemsAdderPack(tempDir.toFile(), source.toFile());

    Path resourcepack = tempDir.resolve("ItemsAdder/contents/exort/resourcepack");
    Path model = resourcepack.resolve("assets/exort/models/storage/storage.json");
    String modelJson = Files.readString(model);
    assertTrue(result.success());
    assertTrue(modelJson.contains("\"0\":\"exort:item/ia_block_storage\""));
    assertTrue(modelJson.contains("\"particle\":\"exort:item/ia_block_storage\""));
    assertTrue(modelJson.contains("\"outside_textures\":\"exort:block/storage\""));
    assertTrue(
        Files.isRegularFile(resourcepack.resolve("assets/exort/textures/block/storage.png")));
    assertEquals(
        "png",
        Files.readString(resourcepack.resolve("assets/exort/textures/item/ia_block_storage.png")));
    String atlas = Files.readString(resourcepack.resolve("assets/minecraft/atlases/blocks.json"));
    assertTrue(atlas.contains("\"resource\":\"exort:block/storage\""));
    assertTrue(atlas.contains("\"resource\":\"exort:item/ia_block_storage\""));
  }

  @Test
  void removeAllDeletesOnlyExortProviderHandoffs() throws IOException {
    Path nexoPack = tempDir.resolve("Nexo/pack/external_packs/zxcmc_exort.zip");
    Path nexoOther = tempDir.resolve("Nexo/pack/external_packs/other.zip");
    Path itemsAdderPack = tempDir.resolve("ItemsAdder/contents/exort/resourcepack/assets/a.txt");
    Path itemsAdderOther =
        tempDir.resolve("ItemsAdder/contents/other/resourcepack/assets/keep.txt");
    Path oraxenPack = tempDir.resolve("Oraxen/pack/uploads/zxcmc_exort.zip");
    Path oraxenOther = tempDir.resolve("Oraxen/pack/uploads/other.zip");
    Files.createDirectories(nexoPack.getParent());
    Files.writeString(nexoPack, "exort");
    Files.writeString(nexoOther, "other");
    Files.createDirectories(itemsAdderPack.getParent());
    Files.writeString(itemsAdderPack, "exort");
    Files.createDirectories(itemsAdderOther.getParent());
    Files.writeString(itemsAdderOther, "other");
    Files.createDirectories(oraxenPack.getParent());
    Files.writeString(oraxenPack, "exort");
    Files.writeString(oraxenOther, "other");

    ResourcePackProviderBridge.removeAll(tempDir.toFile());

    assertFalse(Files.exists(nexoPack));
    assertTrue(Files.isRegularFile(nexoOther));
    assertFalse(Files.exists(tempDir.resolve("ItemsAdder/contents/exort/resourcepack")));
    assertTrue(Files.isRegularFile(itemsAdderOther));
    assertFalse(Files.exists(oraxenPack));
    assertTrue(Files.isRegularFile(oraxenOther));
  }

  private static void writeZip(Path target, Map<String, String> entries) throws IOException {
    Files.createDirectories(target.getParent());
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
  }
}
