package com.zxcmc.exort.core.resourcepack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

record OfficialPackMetadata(String url, String sha1, String version) {
  static OfficialPackMetadata fetch(String metadataUrl, Duration timeout)
      throws IOException, InterruptedException {
    URI metadataUri = URI.create(metadataUrl);
    if (!"https".equalsIgnoreCase(metadataUri.getScheme())) {
      throw new IOException("Official Exort metadata URL must use HTTPS");
    }

    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    HttpRequest request =
        HttpRequest.newBuilder(metadataUri)
            .timeout(timeout)
            .header("Accept", "application/json")
            .GET()
            .build();
    HttpResponse<String> response =
        client.send(
            request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
    if (response.statusCode() != 200) {
      throw new IOException("Official Exort metadata returned HTTP " + response.statusCode());
    }

    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
    String url = string(root, "url");
    String sha1 = string(root, "sha1").toLowerCase(Locale.ROOT);
    String version = string(root, "version");
    validate(url, sha1);
    return new OfficialPackMetadata(url, sha1, version);
  }

  private static void validate(String url, String sha1) throws IOException {
    if (!sha1.matches("(?i)[0-9a-f]{40}")) {
      throw new IOException("Official Exort metadata has invalid sha1");
    }
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new IOException("Official Exort metadata has invalid pack URL", e);
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IOException("Official Exort pack URL must use HTTPS");
    }
  }

  private static String string(JsonObject root, String name) throws IOException {
    if (!root.has(name) || root.get(name).isJsonNull()) {
      if ("version".equals(name)) {
        return "";
      }
      throw new IOException("Official Exort metadata is missing " + name);
    }
    String value = root.get(name).getAsString().trim();
    if (value.isEmpty() && !"version".equals(name)) {
      throw new IOException("Official Exort metadata has empty " + name);
    }
    return value;
  }
}
