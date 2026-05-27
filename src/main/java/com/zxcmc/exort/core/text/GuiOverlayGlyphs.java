package com.zxcmc.exort.core.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class GuiOverlayGlyphs {
  private static final String FONT_RESOURCE = "pack/assets/exort/font/default.json";
  private static final String NAMESPACE = "exort";
  private static final Key FONT = Key.key(NAMESPACE, "default");
  private static final String LEFT_SHIFT = "\uE104";
  private static final String RIGHT_SHIFT = "\uE108\uE106\uE104\uE101";
  private static final Map<String, String> GLYPHS = loadGlyphs();
  private static final Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();

  private GuiOverlayGlyphs() {}

  public static Optional<Component> overlay(
      String configKey, String overlayTexture, Consumer<String> warning) {
    Optional<String> normalized = normalizeConfigTexture(overlayTexture);
    if (normalized.isEmpty()) {
      warnOnce(
          configKey,
          warning,
          "Invalid GUI overlay texture at " + configKey + ": " + overlayTexture);
      return Optional.empty();
    }
    String glyph = GLYPHS.get(normalized.get());
    if (glyph == null) {
      warnOnce(
          configKey,
          warning,
          "Unknown GUI overlay texture at " + configKey + ": " + normalized.get());
      return Optional.empty();
    }
    return Optional.of(
        Component.text(LEFT_SHIFT + glyph + RIGHT_SHIFT, NamedTextColor.WHITE).font(FONT));
  }

  static Map<String, String> glyphs() {
    return GLYPHS;
  }

  static Optional<String> normalizeConfigTexture(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String value = raw.trim();
    if (value.isEmpty()
        || value.indexOf(':') >= 0
        || value.endsWith(".png")
        || value.startsWith("/")
        || value.contains("..")
        || !value.equals(value.toLowerCase(Locale.ROOT))
        || !value.matches("[a-z0-9_./-]+")) {
      return Optional.empty();
    }
    return Optional.of(value);
  }

  private static Map<String, String> loadGlyphs() {
    try (InputStream in =
        GuiOverlayGlyphs.class.getClassLoader().getResourceAsStream(FONT_RESOURCE)) {
      if (in == null) {
        return Map.of();
      }
      JsonObject root =
          JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
              .getAsJsonObject();
      JsonArray providers = root.getAsJsonArray("providers");
      if (providers == null) {
        return Map.of();
      }
      Map<String, String> glyphs = new LinkedHashMap<>();
      for (JsonElement element : providers) {
        if (!element.isJsonObject()) {
          continue;
        }
        JsonObject provider = element.getAsJsonObject();
        if (!"bitmap".equals(stringValue(provider, "type"))) {
          continue;
        }
        Optional<String> texture = normalizeFontTexture(stringValue(provider, "file"));
        String glyph = firstSingleGlyph(provider.getAsJsonArray("chars"));
        if (texture.isPresent() && glyph != null) {
          glyphs.put(texture.get(), glyph);
        }
      }
      return Map.copyOf(glyphs);
    } catch (IOException | RuntimeException ignored) {
      return Map.of();
    }
  }

  private static Optional<String> normalizeFontTexture(String raw) {
    if (raw == null || !raw.startsWith(NAMESPACE + ":") || !raw.endsWith(".png")) {
      return Optional.empty();
    }
    String path = raw.substring((NAMESPACE + ":").length(), raw.length() - ".png".length());
    if (path.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(path);
  }

  private static String firstSingleGlyph(JsonArray chars) {
    if (chars == null || chars.isEmpty()) {
      return null;
    }
    JsonElement first = chars.get(0);
    if (!first.isJsonPrimitive() || !first.getAsJsonPrimitive().isString()) {
      return null;
    }
    String value = first.getAsString();
    return value.codePointCount(0, value.length()) == 1 ? value : null;
  }

  private static String stringValue(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      return null;
    }
    return value.getAsString();
  }

  private static void warnOnce(String configKey, Consumer<String> warning, String message) {
    String key = configKey == null ? "<unknown>" : configKey;
    if (warning != null && WARNED_KEYS.add(key)) {
      warning.accept(message);
    }
  }
}
