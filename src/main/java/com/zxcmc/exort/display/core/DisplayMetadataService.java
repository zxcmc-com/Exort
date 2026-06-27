package com.zxcmc.exort.display.core;

import com.zxcmc.exort.display.culling.DisplayCullingConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Display;

public final class DisplayMetadataService {
  private final DisplayEntityIndex index;
  private final DisplayCullingConfig config;

  public DisplayMetadataService(DisplayEntityIndex index, DisplayCullingConfig config) {
    this.index = index;
    this.config = config;
  }

  public void normalize(Display display) {
    normalize(display, null, true);
  }

  public void normalize(Display display, String localizationKey) {
    normalize(display, localizationKey, false);
  }

  private void normalize(Display display, String localizationKey, boolean preserveLocalizationKey) {
    if (display == null || !display.isValid()) {
      return;
    }
    DisplayRole role = DisplayRole.fromTags(display.getScoreboardTags());
    float viewRange = baseViewRange(role);
    DisplayMetadataNormalizer.normalize(
        display, DisplayMetadataNormalizer.dimensionsFor(display), viewRange);
    if (index != null) {
      if (preserveLocalizationKey) {
        index.register(display);
      } else {
        index.register(display, localizationKey);
      }
    }
  }

  public void resync(Display display) {
    if (display == null || !display.isValid()) {
      return;
    }
    DisplayRole role = DisplayRole.fromTags(display.getScoreboardTags());
    DisplayMetadataNormalizer.resync(display, baseViewRange(role));
    if (index != null) {
      index.register(display);
    }
  }

  public void unregister(Display display) {
    if (display != null && index != null) {
      index.unregister(display.getUniqueId());
    }
  }

  public void rebuildLoadedDisplays() {
    if (index == null) {
      return;
    }
    index.clear();
    for (World world : Bukkit.getWorlds()) {
      for (Display display : world.getEntitiesByClass(Display.class)) {
        if (DisplayRole.fromTags(display.getScoreboardTags()) != null) {
          normalize(display);
        }
      }
    }
  }

  public float baseViewRange(DisplayRole role) {
    double multiplier =
        config == null || config.adaptiveViewRange() == null
            ? 1.0
            : config.adaptiveViewRange().rangeMultiplier(role, 0);
    return (float) Math.max(0.05, DisplayMetadataNormalizer.BASE_VIEW_RANGE * multiplier);
  }
}
