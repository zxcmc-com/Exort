package com.zxcmc.exort.display;

import java.util.Set;

public enum DisplayRole {
  BLOCK,
  WIRE,
  MONITOR_CONTENT,
  HOLOGRAM;

  static DisplayRole fromTags(Set<String> tags) {
    if (tags == null
        || !tags.contains(DisplayTags.DISPLAY_TAG)
        || tags.contains(DisplayTags.BREAK_OVERLAY_TAG)) {
      return null;
    }
    if (tags.contains(DisplayTags.MONITOR_ITEM_TAG)
        || tags.contains(DisplayTags.MONITOR_TEXT_TAG)) {
      return MONITOR_CONTENT;
    }
    if (tags.contains(DisplayTags.HOLOGRAM_TAG)) {
      return HOLOGRAM;
    }
    if (tags.contains(DisplayTags.WIRE_COMPACT_TAG)
        || tags.contains(DisplayTags.WIRE_CENTER_TAG)
        || tags.stream().anyMatch(tag -> tag.startsWith(DisplayTags.WIRE_CONNECTION_PREFIX))) {
      return WIRE;
    }
    return BLOCK;
  }
}
