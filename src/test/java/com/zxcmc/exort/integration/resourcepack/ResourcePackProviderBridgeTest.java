package com.zxcmc.exort.integration.resourcepack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
  void nexoHandoffKeepsPreviousPackWhenCandidateIsCorrupt() throws IOException {
    Path target = tempDir.resolve("Nexo/pack/external_packs/zxcmc_exort.zip");
    writeZip(target, Map.of("assets/exort/previous.json", "previous"));
    byte[] previous = Files.readAllBytes(target);
    Path source = tempDir.resolve("corrupt.raw.zip");
    Files.writeString(source, "not-a-zip");

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.copyNexoPack(tempDir.toFile(), source.toFile());

    assertFalse(result.success());
    assertArrayEquals(previous, Files.readAllBytes(target));
    assertNoProviderStagingPaths(target.getParent());
  }

  @Test
  void nexoHandoffKeepsPreviousPackWhenAtomicCommitFails() throws IOException {
    Path target = tempDir.resolve("Nexo/pack/external_packs/zxcmc_exort.zip");
    writeZip(target, Map.of("assets/exort/previous.json", "previous"));
    byte[] previous = Files.readAllBytes(target);
    Path source = tempDir.resolve("exort.raw.zip");
    writeZip(source, Map.of("assets/exort/current.json", "current"));

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.copyNexoPack(
            tempDir.toFile(),
            source.toFile(),
            (candidate, destination) -> {
              throw new IOException("simulated atomic move failure");
            });

    assertFalse(result.success());
    assertTrue(result.error().contains("simulated atomic move failure"));
    assertArrayEquals(previous, Files.readAllBytes(target));
    assertNoProviderStagingPaths(target.getParent());
  }

  @Test
  void nexoHandoffRejectsTooManyEntriesWithoutReplacingPreviousPack() throws IOException {
    Path target = tempDir.resolve("Nexo/pack/external_packs/zxcmc_exort.zip");
    writeZip(target, Map.of("assets/exort/previous.json", "previous"));
    byte[] previous = Files.readAllBytes(target);
    Path source = tempDir.resolve("too-many-entries.zip");
    writeZipWithEmptyAssets(source, ResourcePackProviderBridge.MAX_PACK_ARCHIVE_ENTRIES + 1);

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.copyNexoPack(tempDir.toFile(), source.toFile());

    assertFalse(result.success());
    assertTrue(result.error().contains("entries"));
    assertArrayEquals(previous, Files.readAllBytes(target));
  }

  @Test
  void nexoHandoffRejectsDeclaredUncompressedLimitWithoutReplacingPreviousPack()
      throws IOException {
    Path target = tempDir.resolve("Nexo/pack/external_packs/zxcmc_exort.zip");
    writeZip(target, Map.of("assets/exort/previous.json", "previous"));
    byte[] previous = Files.readAllBytes(target);
    Path source = tempDir.resolve("oversized-entry.zip");
    writeZipWithDeclaredSize(
        source,
        "assets/exort/oversized.bin",
        ResourcePackProviderBridge.MAX_PACK_UNCOMPRESSED_BYTES + 1L);

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.copyNexoPack(tempDir.toFile(), source.toFile());

    assertFalse(result.success());
    assertTrue(result.error().contains("uncompressed size limit"));
    assertArrayEquals(previous, Files.readAllBytes(target));
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
  void nexoApiHandoffKeepsFallbackWhenRawPackIsCorrupt() throws IOException {
    Path source = tempDir.resolve("corrupt.raw.zip");
    Files.writeString(source, "not-a-zip");
    Path fallback = tempDir.resolve("Nexo/pack/external_packs/zxcmc_exort.zip");
    writeZip(fallback, Map.of("assets/exort/previous.json", "previous"));
    byte[] previous = Files.readAllBytes(fallback);

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.prepareNexoApiHandoff(tempDir.toFile(), source.toFile());

    assertFalse(result.success());
    assertArrayEquals(previous, Files.readAllBytes(fallback));
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
  void itemsAdderHandoffKeepsPreviousTreeWhenArchiveIsCorrupt() throws IOException {
    Path resourcepack = tempDir.resolve("ItemsAdder/contents/exort/resourcepack");
    Path previousFile = resourcepack.resolve("assets/exort/previous.json");
    Files.createDirectories(previousFile.getParent());
    Files.writeString(previousFile, "previous");
    Path source = tempDir.resolve("corrupt.raw.zip");
    Files.writeString(source, "not-a-zip");

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.syncItemsAdderPack(tempDir.toFile(), source.toFile());

    assertFalse(result.success());
    assertEquals("previous", Files.readString(previousFile));
    assertNoProviderStagingPaths(resourcepack.getParent());
  }

  @Test
  void itemsAdderHandoffRollsBackPreviousTreeWhenCandidateCommitFails() throws IOException {
    Path resourcepack = tempDir.resolve("ItemsAdder/contents/exort/resourcepack");
    Path previousFile = resourcepack.resolve("assets/exort/previous.json");
    Files.createDirectories(previousFile.getParent());
    Files.writeString(previousFile, "previous");
    Path source = tempDir.resolve("exort.raw.zip");
    writeZip(source, Map.of("assets/exort/current.json", "current"));
    AtomicInteger moves = new AtomicInteger();

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.syncItemsAdderPack(
            tempDir.toFile(),
            source.toFile(),
            (from, to) -> {
              if (moves.incrementAndGet() == 2) {
                throw new IOException("simulated candidate commit failure");
              }
              Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            });

    assertFalse(result.success());
    assertEquals(3, moves.get());
    assertEquals("previous", Files.readString(previousFile));
    assertFalse(Files.exists(resourcepack.resolve("assets/exort/current.json")));
    assertNoProviderStagingPaths(resourcepack.getParent());
  }

  @Test
  void itemsAdderHandoffRejectsZipSlipWithoutTouchingPreviousTree() throws IOException {
    Path resourcepack = tempDir.resolve("ItemsAdder/contents/exort/resourcepack");
    Path previousFile = resourcepack.resolve("assets/exort/previous.json");
    Files.createDirectories(previousFile.getParent());
    Files.writeString(previousFile, "previous");
    Path source = tempDir.resolve("unsafe.raw.zip");
    writeZip(source, Map.of("assets/../../escaped.txt", "escaped"));

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.syncItemsAdderPack(tempDir.toFile(), source.toFile());

    assertFalse(result.success());
    assertEquals("previous", Files.readString(previousFile));
    assertFalse(Files.exists(tempDir.resolve("escaped.txt")));
    assertNoProviderStagingPaths(resourcepack.getParent());
  }

  @Test
  void itemsAdderHandoffRefusesSymbolicLinkTargetWithoutTouchingItsDestination()
      throws IOException {
    Path external = tempDir.resolve("external-resourcepack");
    Path externalFile = external.resolve("assets/exort/previous.json");
    Files.createDirectories(externalFile.getParent());
    Files.writeString(externalFile, "previous");
    Path resourcepack = tempDir.resolve("ItemsAdder/contents/exort/resourcepack");
    Files.createDirectories(resourcepack.getParent());
    try {
      Files.createSymbolicLink(resourcepack, external);
    } catch (UnsupportedOperationException | IOException unsupported) {
      assumeTrue(false, "Symbolic links are unavailable: " + unsupported.getMessage());
    }
    Path source = tempDir.resolve("exort.raw.zip");
    writeZip(source, Map.of("assets/exort/current.json", "current"));

    ResourcePackProviderBridge.HandoffResult result =
        ResourcePackProviderBridge.syncItemsAdderPack(tempDir.toFile(), source.toFile());

    assertFalse(result.success());
    assertTrue(Files.isSymbolicLink(resourcepack));
    assertEquals("previous", Files.readString(externalFile));
    assertFalse(Files.exists(external.resolve("assets/exort/current.json")));
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

  private static void writeZipWithEmptyAssets(Path target, int entries) throws IOException {
    Files.createDirectories(target.getParent());
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
      for (int i = 0; i < entries; i++) {
        zip.putNextEntry(new ZipEntry("assets/exort/generated/entry_" + i + ".json"));
        zip.closeEntry();
      }
    }
  }

  private static void writeZipWithDeclaredSize(Path target, String entryName, long declaredSize)
      throws IOException {
    byte[] name = entryName.getBytes(StandardCharsets.UTF_8);
    int localHeaderSize = 30 + name.length;
    int centralDirectorySize = 46 + name.length;
    ByteBuffer zip =
        ByteBuffer.allocate(localHeaderSize + centralDirectorySize + 22)
            .order(ByteOrder.LITTLE_ENDIAN);

    zip.putInt(0x04034b50);
    zip.putShort((short) 20);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putInt(0);
    zip.putInt(0);
    zip.putInt(Math.toIntExact(declaredSize));
    zip.putShort((short) name.length);
    zip.putShort((short) 0);
    zip.put(name);

    zip.putInt(0x02014b50);
    zip.putShort((short) 20);
    zip.putShort((short) 20);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putInt(0);
    zip.putInt(0);
    zip.putInt(Math.toIntExact(declaredSize));
    zip.putShort((short) name.length);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putInt(0);
    zip.putInt(0);
    zip.put(name);

    zip.putInt(0x06054b50);
    zip.putShort((short) 0);
    zip.putShort((short) 0);
    zip.putShort((short) 1);
    zip.putShort((short) 1);
    zip.putInt(centralDirectorySize);
    zip.putInt(localHeaderSize);
    zip.putShort((short) 0);

    Files.write(target, zip.array());
  }

  private static void assertNoProviderStagingPaths(Path parent) throws IOException {
    try (var children = Files.list(parent)) {
      assertFalse(
          children.anyMatch(
              path -> {
                String name = path.getFileName().toString();
                return name.contains(".candidate-") || name.contains(".backup-");
              }));
    }
  }
}
