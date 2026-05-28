package com.zxcmc.exort.integration.worldedit;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

final class FaweExtentAccess {
  private static final String ALLOWED_PLUGINS_KEY = "extent.allowed-plugins";
  private static final java.util.Set<String> WARNED_KEYS = ConcurrentHashMap.newKeySet();

  private FaweExtentAccess() {}

  static void allowMarkerExtent(Plugin fawe, Logger logger, String extentClass) {
    boolean modified = false;
    boolean runtimeAllowed = false;
    Throwable runtimeError = null;
    try {
      File configFile = new File(fawe.getDataFolder(), "config.yml");
      if (configFile.isFile()) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        var allowed = config.getStringList(ALLOWED_PLUGINS_KEY);
        if (!allowed.contains(extentClass)) {
          allowed.add(extentClass);
          config.set(ALLOWED_PLUGINS_KEY, allowed);
          config.save(configFile);
          modified = true;
        }
      }
    } catch (Exception ignored) {
    }
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
        try {
          File configFile = new File(fawe.getDataFolder(), "config.yml");
          settingsClass.getMethod("reload", File.class).invoke(settings, configFile);
          extent = settingsClass.getField("EXTENT").get(settings);
          allowedField = extent.getClass().getField("ALLOWED_PLUGINS");
        } catch (Exception ignored) {
        }
      }
      runtimeAllowed = containsString(allowedField.get(extent), extentClass);
    } catch (Throwable err) {
      runtimeError = err;
    }
    if (!runtimeAllowed && logger != null && WARNED_KEYS.add(ALLOWED_PLUGINS_KEY)) {
      String suffix =
          runtimeError == null
              ? ""
              : " Runtime check failed: "
                  + runtimeError.getClass().getSimpleName()
                  + ": "
                  + runtimeError.getMessage();
      logger.warning(
          "[WorldEdit] Could not verify FAWE "
              + ALLOWED_PLUGINS_KEY
              + " contains "
              + extentClass
              + "; Exort marker extent may be rejected by FAWE."
              + suffix);
    }
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
}
