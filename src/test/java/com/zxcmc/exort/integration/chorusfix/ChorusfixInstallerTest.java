package com.zxcmc.exort.integration.chorusfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChorusfixInstallerTest {
  @TempDir Path tempDir;

  @Test
  void parsesLatestReleaseAndSelectsChorusfixJarAsset() throws Exception {
    ChorusfixInstaller.LatestRelease release =
        ChorusfixInstaller.parseLatestRelease(
            """
            {
              "tag_name": "Release_0.2.0",
              "name": "Release 0.2.0",
              "html_url": "https://github.com/phantomfighterxx/Chorusfix/releases/tag/Release_0.2.0",
              "assets": [
                {
                  "name": "source.zip",
                  "browser_download_url": "https://github.com/example/source.zip",
                  "digest": "sha256:abc",
                  "size": 10
                },
                {
                  "name": "Chorusfix-0.2.0.jar",
                  "browser_download_url": "https://github.com/phantomfighterxx/Chorusfix/releases/download/Release_0.2.0/Chorusfix-0.2.0.jar",
                  "digest": "sha256:def",
                  "size": 100
                }
              ]
            }
            """);

    ChorusfixInstaller.ReleaseAsset asset = release.chorusfixJarAsset().orElseThrow();

    assertEquals("Release_0.2.0", release.tagName());
    assertEquals("Chorusfix-0.2.0.jar", asset.name());
    assertEquals("0.2.0", asset.version());
    assertEquals("def", asset.sha256Digest());
  }

  @Test
  void rejectsDownloadedJarWhenDigestDoesNotMatch() throws Exception {
    byte[] jar = jarBytes("Chorusfix", "0.2.0", "new");
    FakeReleaseClient client =
        new FakeReleaseClient(
            release("Chorusfix-0.2.0.jar", "sha256:" + "0".repeat(64), jar.length), jar);
    ChorusfixInstaller installer = new ChorusfixInstaller(tempDir, Optional::empty, client);

    ChorusfixInstaller.InstallResult result = installer.installOrUpdate();

    assertFalse(result.success());
    assertTrue(result.reason().contains("SHA-256 mismatch"));
    assertFalse(Files.exists(tempDir.resolve("Chorusfix-0.2.0.jar")));
  }

  @Test
  void rejectsDownloadedJarWhenMetadataIsNotChorusfix() throws Exception {
    byte[] jar = jarBytes("NotChorusfix", "0.2.0", "new");
    FakeReleaseClient client =
        new FakeReleaseClient(
            release("Chorusfix-0.2.0.jar", "sha256:" + sha256(jar), jar.length), jar);
    ChorusfixInstaller installer = new ChorusfixInstaller(tempDir, Optional::empty, client);

    ChorusfixInstaller.InstallResult result = installer.installOrUpdate();

    assertFalse(result.success());
    assertTrue(result.reason().contains("metadata does not identify Chorusfix"));
    assertFalse(Files.exists(tempDir.resolve("Chorusfix-0.2.0.jar")));
  }

  @Test
  void detectsOnlyTopLevelPluginJars() throws Exception {
    Path topLevel = tempDir.resolve("top-level.jar");
    Path nested = tempDir.resolve("disabled/nested.jar");
    Files.createDirectories(nested.getParent());
    writeJar(topLevel, "Chorusfix", "0.1.0", "top");
    writeJar(nested, "Chorusfix", "0.1.0", "nested");

    assertEquals(
        List.of(topLevel.toAbsolutePath().normalize()),
        ChorusfixInstaller.findInstalledJars(tempDir));
  }

  @Test
  void replacesRenamedInstalledChorusfixJarInPlace() throws Exception {
    Path renamed = tempDir.resolve("my-chorus.jar");
    writeJar(renamed, "Chorusfix", "0.1.0", "old");
    byte[] jar = jarBytes("Chorusfix", "0.2.0", "new");
    FakeReleaseClient client =
        new FakeReleaseClient(
            release("Chorusfix-0.2.0.jar", "sha256:" + sha256(jar), jar.length), jar);
    ChorusfixInstaller installer = new ChorusfixInstaller(tempDir, Optional::empty, client);

    ChorusfixInstaller.InstallResult result = installer.installOrUpdate();

    assertTrue(result.success());
    assertEquals(ChorusfixInstaller.Outcome.UPDATED, result.outcome());
    assertTrue(result.restartRequired());
    assertEquals(renamed.toAbsolutePath().normalize(), result.target());
    assertEquals("new", readMarker(renamed));
    assertFalse(Files.exists(tempDir.resolve("Chorusfix-0.2.0.jar")));
  }

  @Test
  void removesMultipleOldChorusfixJarsBeforeInstallingOfficialAssetName() throws Exception {
    Path first = tempDir.resolve("first.jar");
    Path second = tempDir.resolve("second.jar");
    writeJar(first, "Chorusfix", "0.1.0", "old-a");
    writeJar(second, "Chorusfix", "0.1.0", "old-b");
    byte[] jar = jarBytes("Chorusfix", "0.2.0", "new");
    FakeReleaseClient client =
        new FakeReleaseClient(
            release("Chorusfix-0.2.0.jar", "sha256:" + sha256(jar), jar.length), jar);
    ChorusfixInstaller installer = new ChorusfixInstaller(tempDir, Optional::empty, client);

    ChorusfixInstaller.InstallResult result = installer.installOrUpdate();

    Path official = tempDir.resolve("Chorusfix-0.2.0.jar").toAbsolutePath().normalize();
    assertTrue(result.success());
    assertEquals(ChorusfixInstaller.Outcome.UPDATED, result.outcome());
    assertEquals(official, result.target());
    assertFalse(Files.exists(first));
    assertFalse(Files.exists(second));
    assertEquals("new", readMarker(official));
  }

  @Test
  void skipsDownloadWhenEnabledChorusfixAlreadyMatchesLatestRelease() throws Exception {
    Path current = tempDir.resolve("renamed-current.jar");
    writeJar(current, "Chorusfix", "0.2.0", "current");
    byte[] jar = jarBytes("Chorusfix", "0.2.0", "new");
    FakeReleaseClient client =
        new FakeReleaseClient(
            release("Chorusfix-0.2.0.jar", "sha256:" + sha256(jar), jar.length), jar);
    ChorusfixInstaller.LoadedPlugin loaded =
        new ChorusfixInstaller.LoadedPlugin("0.2.0", Optional.of(current));
    ChorusfixInstaller installer =
        new ChorusfixInstaller(tempDir, () -> Optional.of(loaded), client);

    ChorusfixInstaller.InstallResult result = installer.installOrUpdate();

    assertTrue(result.success());
    assertEquals(ChorusfixInstaller.Outcome.CURRENT, result.outcome());
    assertFalse(result.restartRequired());
    assertEquals(0, client.downloads);
    assertEquals("current", readMarker(current));
  }

  @Test
  void skipsDownloadWhenInstalledChorusfixJarHashMatchesLatestAsset() throws Exception {
    byte[] jar = jarBytes("Chorusfix", "0.2.0", "latest");
    Path renamed = tempDir.resolve("custom-name.jar");
    Files.write(renamed, jar);
    FakeReleaseClient client =
        new FakeReleaseClient(
            release("Chorusfix-0.2.0.jar", "sha256:" + sha256(jar), jar.length), jar);
    ChorusfixInstaller installer = new ChorusfixInstaller(tempDir, Optional::empty, client);

    ChorusfixInstaller.InstallResult result = installer.installOrUpdate();

    assertTrue(result.success());
    assertEquals(ChorusfixInstaller.Outcome.PRESENT, result.outcome());
    assertTrue(result.restartRequired());
    assertEquals(0, client.downloads);
    assertEquals(renamed.toAbsolutePath().normalize(), result.target());
    assertEquals("latest", readMarker(renamed));
    assertFalse(Files.exists(tempDir.resolve("Chorusfix-0.2.0.jar")));
  }

  private static ChorusfixInstaller.LatestRelease release(String name, String digest, long size) {
    return new ChorusfixInstaller.LatestRelease(
        "Release_0.2.0",
        "Release 0.2.0",
        ChorusfixInstaller.MANUAL_URL,
        List.of(
            new ChorusfixInstaller.ReleaseAsset(
                name,
                "https://github.com/phantomfighterxx/Chorusfix/releases/download/Release_0.2.0/"
                    + name,
                digest,
                size)));
  }

  private static void writeJar(Path path, String pluginName, String version, String marker)
      throws IOException {
    Files.write(path, jarBytes(pluginName, version, marker));
  }

  private static byte[] jarBytes(String pluginName, String version, String marker)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      Map<String, String> entries =
          Map.of(
              "paper-plugin.yml",
              "name: " + pluginName + "\nversion: '" + version + "'\n",
              "marker.txt",
              marker);
      for (var entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }

  private static String readMarker(Path jar) throws IOException {
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      try (var in = zip.getInputStream(zip.getEntry("marker.txt"))) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
  }

  private static String sha256(byte[] bytes) throws IOException {
    Path file = Files.createTempFile("chorusfix-test-", ".jar");
    try {
      Files.write(file, bytes);
      return ChorusfixInstaller.sha256Hex(file);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  private static final class FakeReleaseClient implements ChorusfixInstaller.ReleaseClient {
    private final ChorusfixInstaller.LatestRelease release;
    private final byte[] download;
    private int downloads;

    private FakeReleaseClient(ChorusfixInstaller.LatestRelease release, byte[] download) {
      this.release = release;
      this.download = download;
    }

    @Override
    public ChorusfixInstaller.LatestRelease fetchLatestRelease() {
      return release;
    }

    @Override
    public void download(ChorusfixInstaller.ReleaseAsset asset, Path target) throws IOException {
      downloads++;
      Files.write(target, download);
    }
  }
}
