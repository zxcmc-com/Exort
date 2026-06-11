package com.zxcmc.exort.display;

import java.util.Set;

public final class DisplayClassification {
  private DisplayClassification() {}

  public static boolean isManaged(Set<String> tags) {
    return tags != null
        && (tags.contains(DisplayTags.DISPLAY_TAG) || tags.contains(DisplayTags.HOLOGRAM_TAG));
  }

  public static boolean isBreakOverlay(Set<String> tags) {
    return tags != null && tags.contains(DisplayTags.BREAK_OVERLAY_TAG);
  }

  public static boolean isHologram(Set<String> tags) {
    return tags != null && tags.contains(DisplayTags.HOLOGRAM_TAG);
  }

  public static boolean isWireDisplay(Set<String> tags) {
    return DisplayRole.fromTags(tags) == DisplayRole.WIRE;
  }

  public static boolean isAdoptableWireDisplay(Set<String> tags) {
    return isWireDisplay(tags) && !isBreakOverlay(tags) && !isHologram(tags);
  }
}
