package com.zxcmc.exort.integration.chorusfix;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ChorusfixInstaller {
  public static final String MANUAL_URL =
      "https://github.com/phantomfighterxx/Chorusfix/releases/latest";

  private static final String API_URL =
      "https://api.github.com/repos/phantomfighterxx/Chorusfix/releases/latest";
  private static final String OFFICIAL_ASSET_PREFIX = "Chorusfix-";
  private static final String JAR_EXTENSION = ".jar";
  private static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Pattern JSON_STRING_PATTERN =
      Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
  private static final Pattern JSON_NUMBER_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");

  private final Path pluginsDir;
  private final Supplier<Optional<LoadedPlugin>> loadedPluginSupplier;
  private final ReleaseClient releaseClient;

  public ChorusfixInstaller(
      Path pluginsDir, Supplier<Optional<LoadedPlugin>> loadedPluginSupplier) {
    this(pluginsDir, loadedPluginSupplier, new GitHubReleaseClient());
  }

  ChorusfixInstaller(
      Path pluginsDir,
      Supplier<Optional<LoadedPlugin>> loadedPluginSupplier,
      ReleaseClient releaseClient) {
    this.pluginsDir = Objects.requireNonNull(pluginsDir, "pluginsDir").toAbsolutePath().normalize();
    this.loadedPluginSupplier =
        Objects.requireNonNull(loadedPluginSupplier, "loadedPluginSupplier");
    this.releaseClient = Objects.requireNonNull(releaseClient, "releaseClient");
  }

  public InstallResult installOrUpdate() {
    Path temp = null;
    try {
      Files.createDirectories(pluginsDir);
      LatestRelease release = releaseClient.fetchLatestRelease();
      ReleaseAsset asset =
          release
              .chorusfixJarAsset()
              .orElseThrow(() -> new IOException("latest release has no Chorusfix jar asset"));
      Optional<LoadedPlugin> loadedPlugin = loadedPluginSupplier.get();
      List<Path> existingJars = findInstalledJars(pluginsDir);
      if (isEnabledLatest(loadedPlugin, asset)) {
        if (loadedPlugin.get().sourceJar().isPresent()) {
          List<Path> stale = staleCandidates(existingJars, loadedPlugin.get().sourceJar());
          deleteCandidates(stale);
        }
        return InstallResult.current(asset.version());
      }
      Optional<Path> latestHashCandidate = latestHashCandidate(existingJars, asset);
      if (latestHashCandidate.isPresent()) {
        Path target = latestHashCandidate.get();
        List<Path> stale = staleCandidates(existingJars, Optional.of(target));
        deleteCandidates(stale);
        boolean loadedMatchesTarget =
            loadedPlugin
                .flatMap(LoadedPlugin::sourceJar)
                .map(path -> path.toAbsolutePath().normalize().equals(target))
                .orElse(false);
        boolean restartRequired = !loadedMatchesTarget;
        return InstallResult.present(asset.version(), target, restartRequired);
      }

      Path target = resolvedTargetPath(asset, loadedPlugin, existingJars);
      temp = Files.createTempFile(pluginsDir, ".chorusfix-" + UUID.randomUUID() + "-", ".jar.tmp");
      releaseClient.download(asset, temp);
      validateDownloadedJar(temp, asset);

      List<Path> stale = staleCandidates(existingJars, Optional.of(target));
      deleteCandidates(stale);
      moveReplacing(temp, target);
      temp = null;
      Outcome outcome = existingJars.isEmpty() ? Outcome.INSTALLED : Outcome.UPDATED;
      return new InstallResult(outcome, true, asset.version(), target, "");
    } catch (IOException | InterruptedException | RuntimeException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return InstallResult.failed(errorMessage(e));
    } finally {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
          // The failed install path already reports the primary reason.
        }
      }
    }
  }

  public static Optional<LoadedPlugin> findLoadedPlugin(Path pluginsDir) {
    Plugin plugin = Bukkit.getPluginManager().getPlugin(ChorusfixIntegration.PLUGIN_NAME);
    if (plugin == null || !plugin.isEnabled()) {
      return Optional.empty();
    }
    Optional<Path> sourceJar = loadedSourceJar(plugin, pluginsDir);
    return Optional.of(new LoadedPlugin(plugin.getPluginMeta().getVersion(), sourceJar));
  }

  static List<Path> findInstalledJars(Path pluginsDir) throws IOException {
    if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
      return List.of();
    }
    List<Path> result = new ArrayList<>();
    try (var paths = Files.list(pluginsDir)) {
      for (Path path :
          paths
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(JAR_EXTENSION))
              .sorted()
              .toList()) {
        if (isChorusfixJar(path)) {
          result.add(path.toAbsolutePath().normalize());
        }
      }
    }
    return List.copyOf(result);
  }

  static Optional<Path> loadedSourceJar(Plugin plugin, Path pluginsDir) {
    if (plugin == null || pluginsDir == null) {
      return Optional.empty();
    }
    var protectionDomain = plugin.getClass().getProtectionDomain();
    if (protectionDomain == null || protectionDomain.getCodeSource() == null) {
      return Optional.empty();
    }
    try {
      URI uri = protectionDomain.getCodeSource().getLocation().toURI();
      Path path = Path.of(uri).toAbsolutePath().normalize();
      Path normalizedPlugins = pluginsDir.toAbsolutePath().normalize();
      if (!normalizedPlugins.equals(path.getParent()) || !Files.isRegularFile(path)) {
        return Optional.empty();
      }
      return isChorusfixJar(path) ? Optional.of(path) : Optional.empty();
    } catch (IllegalArgumentException | IOException | SecurityException | URISyntaxException e) {
      return Optional.empty();
    }
  }

  static LatestRelease parseLatestRelease(String json) throws IOException {
    if (json == null || json.isBlank()) {
      throw new IOException("empty GitHub release response");
    }
    String tagName = jsonString(json, "tag_name").orElse("");
    String releaseName = jsonString(json, "name").orElse("");
    String htmlUrl = jsonString(json, "html_url").orElse(MANUAL_URL);
    String assetsArray = jsonArray(json, "assets");
    List<ReleaseAsset> assets = new ArrayList<>();
    for (String object : jsonObjects(assetsArray)) {
      String name = jsonString(object, "name").orElse("");
      String downloadUrl = jsonString(object, "browser_download_url").orElse("");
      String digest = jsonString(object, "digest").orElse("");
      long size = jsonNumber(object, "size").orElse(0L);
      if (!name.isBlank()) {
        assets.add(new ReleaseAsset(name, downloadUrl, digest, size));
      }
    }
    return new LatestRelease(tagName, releaseName, htmlUrl, List.copyOf(assets));
  }

  static boolean isChorusfixJar(Path jar) throws IOException {
    return ChorusfixIntegration.PLUGIN_NAME.equals(readPluginName(jar));
  }

  static Optional<String> versionFromAssetName(String name) {
    if (name == null
        || !name.startsWith(OFFICIAL_ASSET_PREFIX)
        || !name.endsWith(JAR_EXTENSION)
        || name.length() <= OFFICIAL_ASSET_PREFIX.length() + JAR_EXTENSION.length()) {
      return Optional.empty();
    }
    String version =
        name.substring(OFFICIAL_ASSET_PREFIX.length(), name.length() - JAR_EXTENSION.length());
    return version.isBlank() || version.contains("/") || version.contains("\\")
        ? Optional.empty()
        : Optional.of(version);
  }

  private static boolean isEnabledLatest(Optional<LoadedPlugin> loadedPlugin, ReleaseAsset asset) {
    return loadedPlugin.map(plugin -> sameVersion(plugin.version(), asset.version())).orElse(false);
  }

  private static boolean sameVersion(String current, String latest) {
    return normalizeVersion(current).equals(normalizeVersion(latest));
  }

  private static String normalizeVersion(String version) {
    if (version == null) {
      return "";
    }
    String normalized = version.trim();
    while (normalized.startsWith("v") || normalized.startsWith("V")) {
      normalized = normalized.substring(1);
    }
    return normalized;
  }

  private static Path chooseTargetPath(
      ReleaseAsset asset, Optional<LoadedPlugin> loadedPlugin, List<Path> existingJars) {
    Optional<Path> loadedSource = loadedPlugin.flatMap(LoadedPlugin::sourceJar);
    if (loadedSource.isPresent()) {
      return loadedSource.get();
    }
    if (existingJars.size() == 1) {
      return existingJars.getFirst();
    }
    return uniqueOfficialTarget(asset.name());
  }

  private static Path uniqueOfficialTarget(String assetName) {
    return Path.of(assetName).getFileName();
  }

  private static List<Path> staleCandidates(List<Path> existingJars, Optional<Path> target) {
    Path normalizedTarget = target.map(path -> path.toAbsolutePath().normalize()).orElse(null);
    return existingJars.stream()
        .map(path -> path.toAbsolutePath().normalize())
        .filter(path -> normalizedTarget == null || !path.equals(normalizedTarget))
        .toList();
  }

  private static Optional<Path> latestHashCandidate(List<Path> existingJars, ReleaseAsset asset) {
    String expected = asset.sha256Digest();
    if (expected.isBlank()) {
      return Optional.empty();
    }
    for (Path path : existingJars) {
      try {
        if (expected.equalsIgnoreCase(sha256Hex(path))) {
          return Optional.of(path.toAbsolutePath().normalize());
        }
      } catch (IOException ignored) {
        // Unreadable candidates will be handled by the normal replace/delete path.
      }
    }
    return Optional.empty();
  }

  private static void deleteCandidates(List<Path> candidates) throws IOException {
    List<Path> ordered =
        candidates.stream().sorted(Comparator.comparing(Path::toString)).distinct().toList();
    for (Path candidate : ordered) {
      Files.deleteIfExists(candidate);
    }
  }

  private Path resolvedTargetPath(
      ReleaseAsset asset, Optional<LoadedPlugin> loadedPlugin, List<Path> jars) {
    Path relative = chooseTargetPath(asset, loadedPlugin, jars);
    if (relative.isAbsolute()) {
      return relative.toAbsolutePath().normalize();
    }
    return pluginsDir.resolve(relative).toAbsolutePath().normalize();
  }

  private static void validateDownloadedJar(Path jar, ReleaseAsset asset) throws IOException {
    if (!Files.isRegularFile(jar)) {
      throw new IOException("downloaded file is missing");
    }
    long size = Files.size(jar);
    if (size <= 0 || size > MAX_DOWNLOAD_BYTES) {
      throw new IOException("downloaded jar size is outside the allowed range");
    }
    String expectedDigest = asset.sha256Digest();
    String actualDigest = sha256Hex(jar);
    if (!expectedDigest.equalsIgnoreCase(actualDigest)) {
      throw new IOException("downloaded jar SHA-256 mismatch");
    }
    if (!isChorusfixJar(jar)) {
      throw new IOException("downloaded jar metadata does not identify Chorusfix");
    }
  }

  private static void moveReplacing(Path source, Path target) throws IOException {
    Files.createDirectories(target.getParent());
    try {
      Files.move(
          source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String readPluginName(Path jar) throws IOException {
    try (JarFile jarFile = new JarFile(jar.toFile())) {
      String name = readPluginName(jarFile, "paper-plugin.yml");
      if (name != null) {
        return name;
      }
      return readPluginName(jarFile, "plugin.yml");
    }
  }

  private static String readPluginName(JarFile jarFile, String metadataPath) throws IOException {
    ZipEntry entry = jarFile.getEntry(metadataPath);
    if (entry == null) {
      return null;
    }
    try (var in = jarFile.getInputStream(entry)) {
      String metadata = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      for (String line : metadata.split("\\R")) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("name:")) {
          continue;
        }
        String value = trimmed.substring("name:".length()).trim();
        int comment = value.indexOf(" #");
        if (comment >= 0) {
          value = value.substring(0, comment).trim();
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))) {
          value = value.substring(1, value.length() - 1);
        }
        return value.isBlank() ? null : value;
      }
    }
    return null;
  }

  static String sha256Hex(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream in = Files.newInputStream(path)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
          digest.update(buffer, 0, read);
        }
      }
      return hex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-256 is unavailable", e);
    }
  }

  private static String hex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      builder.append(String.format(Locale.ROOT, "%02x", value & 0xFF));
    }
    return builder.toString();
  }

  private static String jsonArray(String json, String field) throws IOException {
    String needle = "\"" + field + "\"";
    int fieldIndex = json.indexOf(needle);
    if (fieldIndex < 0) {
      return "";
    }
    int start = json.indexOf('[', fieldIndex + needle.length());
    if (start < 0) {
      return "";
    }
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = start; i < json.length(); i++) {
      char c = json.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '[') {
        depth++;
      } else if (c == ']') {
        depth--;
        if (depth == 0) {
          return json.substring(start + 1, i);
        }
      }
    }
    throw new IOException("unterminated GitHub release assets array");
  }

  private static List<String> jsonObjects(String array) throws IOException {
    List<String> result = new ArrayList<>();
    int start = -1;
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < array.length(); i++) {
      char c = array.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '{') {
        if (depth == 0) {
          start = i;
        }
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && start >= 0) {
          result.add(array.substring(start, i + 1));
          start = -1;
        }
        if (depth < 0) {
          throw new IOException("malformed GitHub release assets array");
        }
      }
    }
    if (depth != 0) {
      throw new IOException("unterminated GitHub release asset object");
    }
    return result;
  }

  private static Optional<String> jsonString(String json, String field) {
    Matcher matcher =
        Pattern.compile(JSON_STRING_PATTERN.pattern().formatted(Pattern.quote(field)))
            .matcher(json);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(unescapeJsonString(matcher.group(1)));
  }

  private static Optional<Long> jsonNumber(String json, String field) {
    Matcher matcher =
        Pattern.compile(JSON_NUMBER_PATTERN.pattern().formatted(Pattern.quote(field)))
            .matcher(json);
    if (!matcher.find()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(matcher.group(1)));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static String unescapeJsonString(String value) {
    StringBuilder result = new StringBuilder(value.length());
    boolean escaped = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!escaped) {
        if (c == '\\') {
          escaped = true;
        } else {
          result.append(c);
        }
        continue;
      }
      result.append(
          switch (c) {
            case '"', '\\', '/' -> c;
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> c;
          });
      escaped = false;
    }
    if (escaped) {
      result.append('\\');
    }
    return result.toString();
  }

  private static String errorMessage(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      return error.getClass().getSimpleName();
    }
    return message.trim();
  }

  public record LoadedPlugin(String version, Optional<Path> sourceJar) {
    public LoadedPlugin {
      version = version == null ? "" : version.trim();
      sourceJar = sourceJar == null ? Optional.empty() : sourceJar.map(Path::normalize);
    }
  }

  public record LatestRelease(
      String tagName, String name, String htmlUrl, List<ReleaseAsset> assets) {
    public LatestRelease {
      tagName = tagName == null ? "" : tagName;
      name = name == null ? "" : name;
      htmlUrl = htmlUrl == null || htmlUrl.isBlank() ? MANUAL_URL : htmlUrl;
      assets = assets == null ? List.of() : List.copyOf(assets);
    }

    Optional<ReleaseAsset> chorusfixJarAsset() {
      return assets.stream()
          .filter(ReleaseAsset::isChorusfixJarAsset)
          .filter(asset -> !asset.sha256Digest().isBlank())
          .findFirst();
    }
  }

  public record ReleaseAsset(String name, String downloadUrl, String digest, long size) {
    public ReleaseAsset {
      name = name == null ? "" : name;
      downloadUrl = downloadUrl == null ? "" : downloadUrl;
      digest = digest == null ? "" : digest;
    }

    String version() {
      return versionFromAssetName(name).orElse("");
    }

    boolean isChorusfixJarAsset() {
      if (version().isBlank() || size <= 0 || size > MAX_DOWNLOAD_BYTES) {
        return false;
      }
      try {
        URI uri = URI.create(downloadUrl);
        return "https".equalsIgnoreCase(uri.getScheme());
      } catch (IllegalArgumentException e) {
        return false;
      }
    }

    String sha256Digest() {
      return digest.regionMatches(true, 0, "sha256:", 0, "sha256:".length())
          ? digest.substring("sha256:".length()).trim()
          : "";
    }
  }

  public record InstallResult(
      Outcome outcome, boolean restartRequired, String version, Path target, String reason) {
    public static InstallResult current(String version) {
      return new InstallResult(Outcome.CURRENT, false, version, null, "");
    }

    public static InstallResult present(String version, Path target, boolean restartRequired) {
      return new InstallResult(Outcome.PRESENT, restartRequired, version, target, "");
    }

    public static InstallResult failed(String reason) {
      return new InstallResult(Outcome.FAILED, false, "", null, reason == null ? "" : reason);
    }

    public boolean success() {
      return outcome != Outcome.FAILED;
    }
  }

  public enum Outcome {
    CURRENT,
    PRESENT,
    INSTALLED,
    UPDATED,
    FAILED
  }

  interface ReleaseClient {
    LatestRelease fetchLatestRelease() throws IOException, InterruptedException;

    void download(ReleaseAsset asset, Path target) throws IOException, InterruptedException;
  }

  private static final class GitHubReleaseClient implements ReleaseClient {
    private final HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public LatestRelease fetchLatestRelease() throws IOException, InterruptedException {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(API_URL))
              .timeout(REQUEST_TIMEOUT)
              .header("Accept", "application/vnd.github+json")
              .header("User-Agent", "Exort-Chorusfix-Installer")
              .GET()
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        throw new IOException(
            "GitHub latest release request failed: HTTP " + response.statusCode());
      }
      return parseLatestRelease(response.body());
    }

    @Override
    public void download(ReleaseAsset asset, Path target) throws IOException, InterruptedException {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(asset.downloadUrl()))
              .timeout(REQUEST_TIMEOUT)
              .header("Accept", "application/octet-stream")
              .header("User-Agent", "Exort-Chorusfix-Installer")
              .GET()
              .build();
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        throw new IOException("Chorusfix download failed: HTTP " + response.statusCode());
      }
      long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
      if (contentLength > MAX_DOWNLOAD_BYTES) {
        throw new IOException("Chorusfix download is larger than the allowed limit");
      }
      try (InputStream in = response.body();
          OutputStream out = Files.newOutputStream(target)) {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = in.read(buffer)) >= 0) {
          total += read;
          if (total > MAX_DOWNLOAD_BYTES) {
            throw new IOException("Chorusfix download exceeded the allowed limit");
          }
          out.write(buffer, 0, read);
        }
      }
    }
  }
}
