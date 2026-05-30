package com.zxcmc.exort.platform;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PaperChorusPlantUpdates {
  public static final String CONFIG_RELATIVE_PATH = "config/paper-global.yml";
  public static final String SETTING_PATH = "block-updates.disable-chorus-plant-updates";

  private static final String SECTION = "block-updates";
  private static final String KEY = "disable-chorus-plant-updates";

  private PaperChorusPlantUpdates() {}

  public static File configFile(File serverRoot) {
    File root = serverRoot != null ? serverRoot : new File(".");
    return new File(new File(root, "config"), "paper-global.yml");
  }

  public static Status read(File serverRoot) {
    File file = configFile(serverRoot);
    if (!file.isFile()) {
      return Status.missing(file);
    }
    try {
      boolean configDisabled = readDisabled(file);
      return Status.present(file, runtimeDisabled().orElse(configDisabled));
    } catch (IOException | InvalidConfigurationException e) {
      return Status.error(file, isAccessDenied(e), errorReason(e, file));
    }
  }

  public static FixResult disable(File serverRoot) {
    File file = configFile(serverRoot);
    if (!file.isFile()) {
      return FixResult.missing(file);
    }
    try {
      boolean configDisabled = readDisabled(file);
      Optional<Boolean> runtimeDisabled = runtimeDisabled();
      boolean activeDisabled = runtimeDisabled.orElse(configDisabled);
      if (configDisabled) {
        return FixResult.present(file, activeDisabled, false, !activeDisabled);
      }
      writeDisabled(file);
      return FixResult.present(file, activeDisabled, true, !activeDisabled);
    } catch (IOException | InvalidConfigurationException e) {
      return FixResult.error(file, isAccessDenied(e), errorReason(e, file));
    }
  }

  public static Optional<Boolean> runtimeDisabled() {
    try {
      Class<?> globalConfiguration =
          Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
      Method get = globalConfiguration.getMethod("get");
      Object global = get.invoke(null);
      if (global == null) {
        return Optional.empty();
      }
      Field blockUpdatesField = global.getClass().getField("blockUpdates");
      Object blockUpdates = blockUpdatesField.get(global);
      if (blockUpdates == null) {
        return Optional.empty();
      }
      Field settingField = blockUpdates.getClass().getField("disableChorusPlantUpdates");
      return Optional.of(settingField.getBoolean(blockUpdates));
    } catch (ReflectiveOperationException
        | LinkageError
        | SecurityException
        | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static boolean readDisabled(File file) throws IOException, InvalidConfigurationException {
    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
    YamlConfiguration config = new YamlConfiguration();
    config.loadFromString(content);
    return config.getBoolean(SETTING_PATH, false);
  }

  private static void writeDisabled(File file) throws IOException {
    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
    String newline = content.contains("\r\n") ? "\r\n" : "\n";
    boolean hadFinalNewline = content.endsWith("\n") || content.endsWith("\r");
    List<String> lines = splitLines(content);

    int sectionIndex = findTopLevelSection(lines);
    if (sectionIndex < 0) {
      appendSection(lines);
    } else {
      setKeyInSection(lines, sectionIndex);
    }

    String updated = String.join(newline, lines);
    if (hadFinalNewline && !updated.endsWith(newline)) {
      updated += newline;
    }
    Files.writeString(file.toPath(), updated, StandardCharsets.UTF_8);
  }

  private static List<String> splitLines(String content) {
    String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
    String[] raw = normalized.split("\n", -1);
    int count = raw.length;
    if (count > 0 && raw[count - 1].isEmpty()) {
      count--;
    }
    List<String> lines = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      lines.add(raw[i]);
    }
    return lines;
  }

  private static int findTopLevelSection(List<String> lines) {
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (indent(line) == 0 && isSectionLine(line)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isSectionLine(String line) {
    String trimmed = line.trim();
    return trimmed.equals(SECTION + ":") || trimmed.startsWith(SECTION + ": #");
  }

  private static void appendSection(List<String> lines) {
    if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
      lines.add("");
    }
    lines.add(SECTION + ":");
    lines.add("  " + KEY + ": true");
  }

  private static void setKeyInSection(List<String> lines, int sectionIndex) {
    int sectionIndent = indent(lines.get(sectionIndex));
    int endIndex = sectionEnd(lines, sectionIndex, sectionIndent);
    String childIndent = childIndent(lines, sectionIndex, endIndex, sectionIndent);

    for (int i = sectionIndex + 1; i < endIndex; i++) {
      String line = lines.get(i);
      if (indent(line) <= sectionIndent || isCommentOrBlank(line)) {
        continue;
      }
      if (line.trim().startsWith(KEY + ":")) {
        lines.set(i, disabledLine(line));
        return;
      }
    }

    lines.add(endIndex, childIndent + KEY + ": true");
  }

  private static int sectionEnd(List<String> lines, int sectionIndex, int sectionIndent) {
    for (int i = sectionIndex + 1; i < lines.size(); i++) {
      String line = lines.get(i);
      if (!isCommentOrBlank(line) && indent(line) <= sectionIndent) {
        return i;
      }
    }
    return lines.size();
  }

  private static String childIndent(
      List<String> lines, int sectionIndex, int endIndex, int sectionIndent) {
    for (int i = sectionIndex + 1; i < endIndex; i++) {
      String line = lines.get(i);
      if (!isCommentOrBlank(line) && indent(line) > sectionIndent) {
        return line.substring(0, firstNonWhitespace(line));
      }
    }
    return " ".repeat(sectionIndent + 2);
  }

  private static String disabledLine(String line) {
    int first = firstNonWhitespace(line);
    String indent = line.substring(0, first);
    String comment = trailingComment(line);
    return indent + KEY + ": true" + comment;
  }

  private static String trailingComment(String line) {
    int colon = line.indexOf(':');
    int hash = line.indexOf('#', colon + 1);
    if (hash < 0) {
      return "";
    }
    String comment = line.substring(hash).trim();
    return comment.isEmpty() ? "" : " " + comment;
  }

  private static int indent(String line) {
    return firstNonWhitespace(line);
  }

  private static int firstNonWhitespace(String line) {
    int i = 0;
    while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
      i++;
    }
    return i;
  }

  private static boolean isCommentOrBlank(String line) {
    String trimmed = line.trim();
    return trimmed.isEmpty() || trimmed.startsWith("#");
  }

  static String errorReason(Exception error, File file) {
    if (error instanceof AccessDeniedException) {
      return "access denied";
    }
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      return error.getClass().getSimpleName();
    }
    String trimmed = message.trim();
    if (isOnlyFilePath(trimmed, file)) {
      return error.getClass().getSimpleName();
    }
    return error.getClass().getSimpleName() + ": " + trimmed;
  }

  private static boolean isAccessDenied(Exception error) {
    return error instanceof AccessDeniedException;
  }

  private static boolean isOnlyFilePath(String message, File file) {
    return message.equals(file.getPath())
        || message.equals(file.getAbsolutePath())
        || message.equals(file.toPath().toString());
  }

  public record Status(
      File file, State state, boolean disabled, boolean accessDenied, String error) {
    public static Status present(File file, boolean disabled) {
      return new Status(file, State.PRESENT, disabled, false, "");
    }

    public static Status missing(File file) {
      return new Status(file, State.MISSING, false, false, "");
    }

    public static Status error(File file, boolean accessDenied, String error) {
      return new Status(file, State.ERROR, false, accessDenied, error == null ? "" : error);
    }
  }

  public record FixResult(
      File file,
      State state,
      boolean previousDisabled,
      boolean changed,
      boolean restartRequired,
      boolean accessDenied,
      String error) {
    public static FixResult present(File file, boolean previousDisabled, boolean changed) {
      return present(file, previousDisabled, changed, changed);
    }

    public static FixResult present(
        File file, boolean previousDisabled, boolean changed, boolean restartRequired) {
      return new FixResult(
          file, State.PRESENT, previousDisabled, changed, restartRequired, false, "");
    }

    public static FixResult missing(File file) {
      return new FixResult(file, State.MISSING, false, false, false, false, "");
    }

    public static FixResult error(File file, boolean accessDenied, String error) {
      return new FixResult(
          file, State.ERROR, false, false, false, accessDenied, error == null ? "" : error);
    }
  }

  public enum State {
    PRESENT,
    MISSING,
    ERROR
  }
}
