package com.zxcmc.exort.infra.resourcepack.hosting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class LobFileUploader {
  private static final String ENDPOINT = "https://lobfile.com/api/v3/upload";

  private LobFileUploader() {}

  static String upload(File packFile, String sha1, String apiKey) throws IOException {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IOException("LobFile API key is missing");
    }
    String boundary = "----ExortPackBoundary" + System.nanoTime();
    HttpURLConnection connection =
        (HttpURLConnection) URI.create(ENDPOINT).toURL().openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(60000);
    connection.setRequestProperty("X-API-Key", apiKey.trim());
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    try (var out = connection.getOutputStream()) {
      writeTextPart(out, boundary, "sha_1", sha1);
      writeFilePart(out, boundary, "file", "pack.zip", packFile);
      out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    }
    int code = connection.getResponseCode();
    byte[] response;
    try (var in =
        code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream()) {
      response = in == null ? new byte[0] : in.readAllBytes();
    }
    if (code < 200 || code >= 300) {
      throw new IOException("LobFile upload failed with HTTP " + code);
    }
    JsonObject json =
        JsonParser.parseString(new String(response, StandardCharsets.UTF_8)).getAsJsonObject();
    if (!json.has("success") || !json.get("success").getAsBoolean() || !json.has("url")) {
      throw new IOException("LobFile upload response did not contain a successful URL");
    }
    return json.get("url").getAsString();
  }

  private static void writeTextPart(
      java.io.OutputStream out, String boundary, String name, String value) throws IOException {
    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(
        ("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.write(value.getBytes(StandardCharsets.UTF_8));
    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private static void writeFilePart(
      java.io.OutputStream out, String boundary, String name, String filename, File file)
      throws IOException {
    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(
        ("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.write("Content-Type: application/zip\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    Files.copy(file.toPath(), out);
    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }
}
