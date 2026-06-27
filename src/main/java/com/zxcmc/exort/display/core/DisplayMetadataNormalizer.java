package com.zxcmc.exort.display.core;

import com.zxcmc.exort.debug.PerfStats;
import java.util.Set;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

public final class DisplayMetadataNormalizer {
  public static final float BASE_VIEW_RANGE = 1.0f;
  private static final DisplayDimensions FULL_BLOCK = new DisplayDimensions(1.0f, 1.0f);
  private static final DisplayDimensions SMALL_SAMPLE = new DisplayDimensions(0.75f, 0.75f);
  private static final DisplayDimensions MONITOR_TEXT = new DisplayDimensions(1.0f, 0.5f);

  private DisplayMetadataNormalizer() {}

  public static void normalize(Display display, DisplayDimensions dimensions) {
    normalize(display, dimensions, BASE_VIEW_RANGE);
  }

  public static void normalize(Display display, DisplayDimensions dimensions, float viewRange) {
    if (display == null || !display.isValid()) {
      return;
    }
    DisplayDimensions safe = dimensions == null ? FULL_BLOCK : dimensions.sanitized();
    try {
      display.setInvulnerable(true);
      display.setSilent(true);
      display.setBillboard(Display.Billboard.FIXED);
      display.setTeleportDuration(0);
      display.setViewRange(Math.max(0.05f, viewRange));
      display.setDisplayWidth(safe.width());
      display.setDisplayHeight(safe.height());
      PerfStats.incrementCounter("display.metadata.normalized");
    } catch (RuntimeException ignored) {
      // Display metadata can disappear while chunks/entities are being unloaded.
    }
  }

  public static void resync(Display display, float viewRange) {
    if (display == null || !display.isValid()) {
      return;
    }
    try {
      normalize(display, dimensionsFor(display), viewRange);
      if (display instanceof ItemDisplay itemDisplay) {
        ItemStack stack = itemDisplay.getItemStack();
        if (stack != null) {
          itemDisplay.setItemStack(stack.clone());
        }
      }
      display.setTransformation(display.getTransformation());
      PerfStats.incrementCounter("display.metadata.resync");
    } catch (RuntimeException ignored) {
      // Display metadata can disappear while chunks/entities are being unloaded.
    }
  }

  public static DisplayDimensions dimensionsFor(Display display) {
    if (display == null) {
      return FULL_BLOCK;
    }
    return dimensionsForTags(display.getScoreboardTags());
  }

  static DisplayDimensions dimensionsForTags(Set<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return FULL_BLOCK;
    }
    if (tags.contains(DisplayTags.MONITOR_TEXT_TAG)) {
      return MONITOR_TEXT;
    }
    if (tags.contains(DisplayTags.MONITOR_ITEM_TAG) || tags.contains(DisplayTags.HOLOGRAM_TAG)) {
      return SMALL_SAMPLE;
    }
    return FULL_BLOCK;
  }

  public record DisplayDimensions(float width, float height) {
    private DisplayDimensions sanitized() {
      return new DisplayDimensions(Math.max(0.05f, width), Math.max(0.05f, height));
    }
  }
}
