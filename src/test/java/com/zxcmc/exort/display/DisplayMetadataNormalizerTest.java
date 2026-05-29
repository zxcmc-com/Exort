package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DisplayMetadataNormalizerTest {
  @Test
  void usesFullBlockDimensionsForMainBlockDisplays() {
    DisplayMetadataNormalizer.DisplayDimensions dimensions =
        DisplayMetadataNormalizer.dimensionsForTags(Set.of(DisplayTags.DISPLAY_TAG, "storage"));

    assertEquals(1.0f, dimensions.width());
    assertEquals(1.0f, dimensions.height());
  }

  @Test
  void usesSmallDimensionsForHologramsAndMonitorItems() {
    DisplayMetadataNormalizer.DisplayDimensions monitorItem =
        DisplayMetadataNormalizer.dimensionsForTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.MONITOR_ITEM_TAG));
    DisplayMetadataNormalizer.DisplayDimensions hologram =
        DisplayMetadataNormalizer.dimensionsForTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.HOLOGRAM_TAG));

    assertEquals(0.75f, monitorItem.width());
    assertEquals(0.75f, monitorItem.height());
    assertEquals(0.75f, hologram.width());
    assertEquals(0.75f, hologram.height());
  }

  @Test
  void usesFlatDimensionsForMonitorText() {
    DisplayMetadataNormalizer.DisplayDimensions dimensions =
        DisplayMetadataNormalizer.dimensionsForTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.MONITOR_TEXT_TAG));

    assertEquals(1.0f, dimensions.width());
    assertEquals(0.5f, dimensions.height());
  }
}
