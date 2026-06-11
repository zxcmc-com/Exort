package com.zxcmc.exort.integration.worldedit;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

final class FaweExtentAccess {
  private static final String ALLOWED_PLUGINS_KEY = "extent.allowed-plugins";
  private static final java.util.Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();

  private FaweExtentAccess() {}

  static Result allowMarkerExtent(Plugin fawe, String extentClass) {
    File configFile = new File(fawe.getDataFolder(), "config.yml");
    ConfigResult configResult = allowMarkerExtentInConfig(configFile, extentClass);
    boolean modified = configResult.modified() && configResult.saved();
    boolean runtimeAllowed = false;
    Throwable runtimeError = null;
    boolean reloadAttempted = false;
    boolean reloadSucceeded = false;
    Throwable reloadError = null;
    try {
      Class<?> settingsClass = Class.forName("com.fastasyncworldedit.core.configuration.Settings");
      Object settings = settingsClass.getMethod("settings").invoke(null);
      Object extent = settingsClass.getField("EXTENT").get(settings);
      var allowedField = extent.getClass().getField("ALLOWED_PLUGINS");
      Object value = allowedField.get(extent);
      if (value instanceof java.util.List<?> list) {
        if (!list.contains(extentClass)) {
          try {
            @SuppressWarnings("unchecked")
            java.util.List<String> mutable = (java.util.List<String>) list;
            mutable.add(extentClass);
          } catch (UnsupportedOperationException ignored) {
            java.util.List<String> copy = new java.util.ArrayList<>();
            for (Object item : list) {
              if (item != null) {
                copy.add(item.toString());
              }
            }
            if (!copy.contains(extentClass)) {
              copy.add(extentClass);
            }
            allowedField.set(extent, copy);
          }
        }
      }
      if (modified) {
        reloadAttempted = true;
        try {
          settingsClass.getMethod("reload", File.class).invoke(settings, configFile);
          reloadSucceeded = true;
          extent = settingsClass.getField("EXTENT").get(settings);
          allowedField = extent.getClass().getField("ALLOWED_PLUGINS");
        } catch (Exception error) {
          reloadError = error;
        }
      }
      runtimeAllowed = containsString(allowedField.get(extent), extentClass);
    } catch (Throwable err) {
      runtimeError = err;
    }
    return new Result(
        configResult,
        reloadAttempted,
        reloadSucceeded,
        describe(reloadError),
        runtimeAllowed,
        describe(runtimeError));
  }

  static ConfigResult allowMarkerExtentInConfig(File configFile, String extentClass) {
    String path = configFile == null ? "<unknown>" : configFile.getAbsolutePath();
    if (configFile == null || !configFile.isFile()) {
      return new ConfigResult(path, false, false, false, null);
    }
    try {
      YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
      var allowed = config.getStringList(ALLOWED_PLUGINS_KEY);
      if (allowed.contains(extentClass)) {
        return new ConfigResult(path, true, false, false, null);
      }
      allowed.add(extentClass);
      config.set(ALLOWED_PLUGINS_KEY, allowed);
      config.save(configFile);
      return new ConfigResult(path, true, true, true, null);
    } catch (Exception error) {
      return new ConfigResult(path, true, true, false, describe(error));
    }
  }

  static boolean shouldLogWarning(Result result) {
    if (result == null) {
      return false;
    }
    return result.hasFailure() && WARNED_KEYS.add(ALLOWED_PLUGINS_KEY);
  }

  private static String describe(Throwable error) {
    if (error == null) {
      return null;
    }
    String message = error.getMessage();
    return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
  }

  private static boolean containsString(Object value, String expected) {
    if (value instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        if (expected.equals(String.valueOf(item))) {
          return true;
        }
      }
    }
    return false;
  }

  record ConfigResult(
      String path, boolean fileFound, boolean modified, boolean saved, String error) {}

  record Result(
      ConfigResult config,
      boolean reloadAttempted,
      boolean reloadSucceeded,
      String reloadError,
      boolean runtimeAllowed,
      String runtimeError) {
    boolean hasFailure() {
      return !runtimeAllowed || config.error() != null || reloadError != null;
    }

    String logMessage(String extentClass) {
      return "[WorldEdit] FAWE marker extent access: class="
          + extentClass
          + ", config="
          + config.path()
          + ", fileFound="
          + config.fileFound()
          + ", modified="
          + config.modified()
          + ", saved="
          + config.saved()
          + ", configError="
          + none(config.error())
          + ", reloadAttempted="
          + reloadAttempted
          + ", reloadSucceeded="
          + reloadSucceeded
          + ", reloadError="
          + none(reloadError)
          + ", runtimeAllowed="
          + runtimeAllowed
          + ", runtimeError="
          + none(runtimeError);
    }

    private static String none(String value) {
      return value == null || value.isBlank() ? "none" : value;
    }
  }
}
