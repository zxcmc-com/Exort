package com.zxcmc.exort.core.update;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.logging.ExortLog;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;

public final class UpdateChecker {
  private static final String BUILD_GRADLE_URL =
      "https://raw.githubusercontent.com/phantomfighterxx/Exort/master/build.gradle";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Pattern VERSION_DECLARATION =
      Pattern.compile("(?m)^\\s*version\\s*=\\s*['\"]([^'\"]+)['\"]\\s*$");

  private final ExortPlugin plugin;

  public UpdateChecker(ExortPlugin plugin) {
    this.plugin = plugin;
  }

  public void checkAsync() {
    if (!plugin.getConfig().getBoolean("updateCheck.enabled", true)) {
      return;
    }
    String currentVersion = plugin.getVersion();
    Bukkit.getScheduler()
        .runTaskAsynchronously(
            plugin,
            () -> {
              try {
                check(currentVersion);
              } catch (IOException | RuntimeException ignored) {
                // Update checks must never add startup noise or affect server availability.
              } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              }
            });
  }

  private void check(String currentVersion) throws IOException, InterruptedException {
    Optional<String> remoteVersion = fetchLatestVersion();
    if (remoteVersion.isEmpty()) {
      return;
    }
    int comparison = compareVersions(currentVersion, remoteVersion.get());
    Bukkit.getScheduler()
        .runTask(plugin, () -> logResult(currentVersion, remoteVersion.get(), comparison));
  }

  private void logResult(String currentVersion, String remoteVersion, int comparison) {
    if (!plugin.isEnabled()) {
      return;
    }
    if (comparison < 0) {
      ExortLog.warn(
          "Exort Storage Network update available: v"
              + remoteVersion
              + " (running v"
              + currentVersion
              + ").");
    } else {
      ExortLog.success("Exort Storage Network v" + currentVersion + " is up to date.");
    }
  }

  private Optional<String> fetchLatestVersion() throws IOException, InterruptedException {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(BUILD_GRADLE_URL))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "text/plain")
            .header("User-Agent", "Exort-Update-Checker/" + plugin.getVersion())
            .GET()
            .build();
    HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200) {
      return Optional.empty();
    }
    return extractVersion(response.body());
  }

  static Optional<String> extractVersion(String buildGradle) {
    if (buildGradle == null || buildGradle.isBlank()) {
      return Optional.empty();
    }
    Matcher matcher = VERSION_DECLARATION.matcher(buildGradle);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String version = matcher.group(1).trim();
    return version.isEmpty() ? Optional.empty() : Optional.of(version);
  }

  static int compareVersions(String currentVersion, String latestVersion) throws IOException {
    ParsedVersion current = ParsedVersion.parse(currentVersion);
    ParsedVersion latest = ParsedVersion.parse(latestVersion);
    return current.compareTo(latest);
  }

  private record ParsedVersion(List<Integer> numbers, String qualifier)
      implements Comparable<ParsedVersion> {
    static ParsedVersion parse(String raw) throws IOException {
      if (raw == null) {
        throw new IOException("Empty version");
      }
      String version = raw.trim();
      if (version.startsWith("v") || version.startsWith("V")) {
        version = version.substring(1);
      }
      if (version.isEmpty() || !Character.isDigit(version.charAt(0))) {
        throw new IOException("Unsupported version: " + raw);
      }

      List<Integer> numbers = new ArrayList<>();
      int index = 0;
      while (index < version.length()) {
        int start = index;
        while (index < version.length() && Character.isDigit(version.charAt(index))) {
          index++;
        }
        if (start == index) {
          break;
        }
        try {
          numbers.add(Integer.parseInt(version.substring(start, index)));
        } catch (NumberFormatException e) {
          throw new IOException("Unsupported version: " + raw, e);
        }
        if (index >= version.length() || version.charAt(index) != '.') {
          break;
        }
        index++;
      }
      if (numbers.isEmpty()) {
        throw new IOException("Unsupported version: " + raw);
      }

      String qualifier = index < version.length() ? version.substring(index) : "";
      return new ParsedVersion(List.copyOf(numbers), qualifier);
    }

    @Override
    public int compareTo(ParsedVersion other) {
      int max = Math.max(numbers.size(), other.numbers.size());
      for (int i = 0; i < max; i++) {
        int left = i < numbers.size() ? numbers.get(i) : 0;
        int right = i < other.numbers.size() ? other.numbers.get(i) : 0;
        if (left != right) {
          return Integer.compare(left, right);
        }
      }
      if (qualifier.isBlank() && other.qualifier.isBlank()) {
        return 0;
      }
      if (qualifier.isBlank()) {
        return 1;
      }
      if (other.qualifier.isBlank()) {
        return -1;
      }
      return qualifier.compareToIgnoreCase(other.qualifier);
    }
  }
}
