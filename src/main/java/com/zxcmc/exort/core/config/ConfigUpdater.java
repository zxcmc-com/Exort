package com.zxcmc.exort.core.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigUpdater {
  private ConfigUpdater() {}

  public static void update(JavaPlugin plugin, String resourceName) {
    File dataFolder = plugin.getDataFolder();
    if (!dataFolder.exists()) {
      dataFolder.mkdirs();
    }
    File configFile = new File(dataFolder, resourceName);
    boolean existed = configFile.exists();
    YamlConfiguration userCfg = new YamlConfiguration();
    List<String> userLines = List.of();
    if (existed) {
      try {
        userCfg.load(configFile);
      } catch (Exception e) {
        plugin
            .getLogger()
            .warning("Failed to load existing config, using defaults: " + e.getMessage());
      }
      try {
        userLines = readAllLines(configFile);
      } catch (IOException ignored) {
        userLines = List.of();
      }
    }

    YamlConfiguration defaultCfg = new YamlConfiguration();
    try (InputStream in = plugin.getResource(resourceName)) {
      if (in != null) {
        defaultCfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      plugin.getLogger().severe("Failed to read default " + resourceName + ": " + e.getMessage());
      return;
    }

    Set<String> defaultKeys = defaultCfg.getKeys(true);

    // Add missing defaults
    for (String key : defaultKeys) {
      if (key.startsWith("tiers.") && existed) continue; // let user fully control tiers
      if (!userCfg.contains(key)) {
        userCfg.set(key, defaultCfg.get(key));
      }
    }

    // Build output preserving default comments/order for scalar values
    String merged =
        mergeWithDefaults(defaultCfg, userCfg, userLines, resourceName, plugin, existed);

    try (Writer writer =
        new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
      writer.write(merged);
    } catch (IOException e) {
      plugin.getLogger().severe("Failed to write merged config: " + e.getMessage());
    }
  }

  private static String mergeWithDefaults(
      YamlConfiguration defaults,
      YamlConfiguration user,
      List<String> userLines,
      String resourceName,
      JavaPlugin plugin,
      boolean existed) {
    List<String> lines = new ArrayList<>();
    try (InputStream in = plugin.getResource(resourceName)) {
      if (in != null) {
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            lines.add(line);
          }
        }
      }
    } catch (IOException e) {
      plugin.getLogger().severe("Failed to read default config for merging: " + e.getMessage());
    }

    List<String> rawTiersBody = existed ? extractSectionBody(userLines, "tiers") : null;

    StringBuilder out = new StringBuilder();
    Deque<String> pathStack = new ArrayDeque<>();

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        out.append(line).append(System.lineSeparator());
        continue;
      }
      String withoutComment =
          line.contains("#") ? line.substring(0, line.indexOf('#')).trim() : trimmed;
      int indent = countLeadingSpaces(line);
      while (!pathStack.isEmpty() && indent <= indentOf(pathStack.peek())) {
        pathStack.pop();
      }
      String keyPart = keyFromLine(line);
      if (keyPart == null) {
        out.append(line).append(System.lineSeparator());
        continue;
      }
      String fullPath = buildPath(pathStack, keyPart);
      boolean isSection = false;
      int colonIdx = withoutComment.indexOf(':');
      if (colonIdx >= 0) {
        String after = withoutComment.substring(colonIdx + 1).trim();
        if (after.isEmpty()) {
          isSection = true;
        }
      }
      if (isSection) {
        // Special handling for tiers: emit user section and skip defaults when user fully controls
        // it
        if ("tiers".equalsIgnoreCase(fullPath) && existed) {
          out.append(line).append(System.lineSeparator());
          if (rawTiersBody != null) {
            List<String> normalized = normalizeIndent(rawTiersBody, indent + 2);
            for (String l : normalized) {
              out.append(l).append(System.lineSeparator());
            }
          } else {
            Object tiersObj = user.getConfigurationSection("tiers");
            if (tiersObj instanceof ConfigurationSection userTiers) {
              renderSection(out, userTiers, indent + 2);
            }
          }
          // skip lines until we exit the tiers section in defaults
          int baseIndent = indent;
          i++;
          while (i < lines.size()) {
            String next = lines.get(i);
            int nextIndent = countLeadingSpaces(next);
            String ntrim = next.trim();
            if (!ntrim.isEmpty() && !ntrim.startsWith("#") && nextIndent <= baseIndent) {
              i--; // step back one so outer loop processes this line
              break;
            }
            i++;
          }
          continue;
        }
        if (fullPath.startsWith("tiers.") && existed && !user.contains(fullPath)) {
          // skip default tier subtree that user removed
          int baseIndent = indent;
          i++;
          while (i < lines.size()) {
            String next = lines.get(i);
            int nextIndent = countLeadingSpaces(next);
            String ntrim = next.trim();
            if (!ntrim.isEmpty() && !ntrim.startsWith("#") && nextIndent <= baseIndent) {
              i--; // step back for outer loop
              break;
            }
            i++;
          }
          continue;
        }
        pathStack.push(keyWithIndent(keyPart, indent));
        out.append(line).append(System.lineSeparator());
      } else {
        if (fullPath.startsWith("tiers.") && existed && !user.contains(fullPath)) {
          continue; // skip default tier entries if user removed them
        }
        Object val = user.contains(fullPath) ? user.get(fullPath) : defaults.get(fullPath);
        String rendered = scalarToString(val);
        out.append(spaces(indent))
            .append(keyPart)
            .append(": ")
            .append(rendered)
            .append(System.lineSeparator());
      }
    }

    // Preserve removed/unknown options for reference (except tiers.* which are user-controlled).
    if (existed) {
      Set<String> unknownLeaves = collectUnknownLeaves(defaults, user);
      if (!unknownLeaves.isEmpty()) {
        out.append(System.lineSeparator());
        out.append("# Removed/unknown options preserved for reference:")
            .append(System.lineSeparator());
        for (String key : unknownLeaves) {
          out.append("# ")
              .append(key)
              .append(": ")
              .append(scalarToString(user.get(key)))
              .append(System.lineSeparator());
        }
      }
    }
    return out.toString();
  }

  private static void renderSection(StringBuilder out, ConfigurationSection section, int indent) {
    for (String key : section.getKeys(false)) {
      Object val = section.get(key);
      if (val instanceof ConfigurationSection cs) {
        out.append(spaces(indent)).append(key).append(":").append(System.lineSeparator());
        renderSection(out, cs, indent + 2);
      } else {
        out.append(spaces(indent))
            .append(key)
            .append(": ")
            .append(scalarToString(val))
            .append(System.lineSeparator());
      }
    }
  }

  private static int countLeadingSpaces(String line) {
    int i = 0;
    while (i < line.length() && line.charAt(i) == ' ') i++;
    return i;
  }

  private static String keyFromLine(String line) {
    String trimmed = line.trim();
    int colon = trimmed.indexOf(':');
    if (colon <= 0) return null;
    return trimmed.substring(0, colon).trim();
  }

  private static String buildPath(Deque<String> stack, String key) {
    if (stack.isEmpty()) return key;
    StringBuilder sb = new StringBuilder();
    List<String> parts = new ArrayList<>();
    for (String s : stack) {
      parts.add(s.substring(s.indexOf('|') + 1)); // remove indent marker
    }
    Collections.reverse(parts);
    for (String part : parts) {
      if (!part.isEmpty()) {
        if (sb.length() > 0) sb.append('.');
        sb.append(part);
      }
    }
    if (sb.length() > 0) sb.append('.');
    sb.append(key);
    return sb.toString();
  }

  private static String keyWithIndent(String key, int indent) {
    return indent + "|" + key;
  }

  private static int indentOf(String stored) {
    int idx = stored.indexOf('|');
    if (idx == -1) return 0;
    try {
      return Integer.parseInt(stored.substring(0, idx));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static String spaces(int n) {
    return " ".repeat(Math.max(0, n));
  }

  private static String scalarToString(Object val) {
    if (val == null) return "null";
    YamlConfiguration tmp = new YamlConfiguration();
    tmp.set("v", val);
    String serialized = tmp.saveToString();
    int idx = serialized.indexOf(':');
    if (idx == -1) return val.toString();
    String after = serialized.substring(idx + 1).trim();
    int newline = after.indexOf('\n');
    if (newline >= 0) {
      after = after.substring(0, newline).trim();
    }
    return after;
  }

  private static List<String> readAllLines(File file) throws IOException {
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        lines.add(line);
      }
    }
    return lines;
  }

  /**
   * Extracts the body of a top-level YAML section (excluding the header line). Returned lines are
   * raw (with original indentation).
   */
  private static List<String> extractSectionBody(List<String> lines, String sectionKey) {
    if (lines == null || lines.isEmpty()) return null;
    String headerPrefix = sectionKey + ":";
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      String withoutComment =
          line.contains("#") ? line.substring(0, line.indexOf('#')).trim() : trimmed;
      if (!withoutComment.startsWith(headerPrefix)) continue;
      int headerIndent = countLeadingSpaces(line);
      List<String> body = new ArrayList<>();
      for (int j = i + 1; j < lines.size(); j++) {
        String next = lines.get(j);
        String ntrim = next.trim();
        if (!ntrim.isEmpty() && !ntrim.startsWith("#")) {
          int nextIndent = countLeadingSpaces(next);
          if (nextIndent <= headerIndent) {
            break;
          }
        }
        body.add(next);
      }
      return body;
    }
    return null;
  }

  /**
   * Normalizes indentation of a section body so that its minimum meaningful indent becomes
   * targetIndent.
   */
  private static List<String> normalizeIndent(List<String> body, int targetIndent) {
    if (body == null || body.isEmpty()) return List.of();
    int minIndent = Integer.MAX_VALUE;
    for (String line : body) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
      minIndent = Math.min(minIndent, countLeadingSpaces(line));
    }
    if (minIndent == Integer.MAX_VALUE) {
      // body has only comments/blank lines, return as-is
      return body;
    }
    int shift = targetIndent - minIndent;
    if (shift == 0) return body;
    List<String> out = new ArrayList<>(body.size());
    for (String line : body) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        out.add(line);
        continue;
      }
      int indent = countLeadingSpaces(line);
      int newIndent = Math.max(0, indent + shift);
      out.add(spaces(newIndent) + line.substring(Math.min(indent, line.length())));
    }
    return out;
  }

  private static Set<String> collectUnknownLeaves(
      YamlConfiguration defaults, YamlConfiguration user) {
    Set<String> defaultKeys = defaults.getKeys(true);
    Set<String> out = new TreeSet<>();
    for (String key : user.getKeys(true)) {
      if ("tiers".equalsIgnoreCase(key) || key.startsWith("tiers.")) continue;
      if (defaultKeys.contains(key)) continue;
      if (user.isConfigurationSection(key)) continue;
      out.add(key);
    }
    return out;
  }
}
