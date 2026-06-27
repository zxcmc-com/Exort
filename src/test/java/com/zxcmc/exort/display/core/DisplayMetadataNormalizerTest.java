package com.zxcmc.exort.display.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DisplayMetadataNormalizerTest {
  @Test
  void mainBlockDisplaysKeepPositiveSquareDimensions() {
    DisplayMetadataNormalizer.DisplayDimensions dimensions =
        DisplayMetadataNormalizer.dimensionsForTags(Set.of(DisplayTags.DISPLAY_TAG, "storage"));

    assertTrue(dimensions.width() > 0.0f);
    assertEquals(dimensions.width(), dimensions.height());
  }

  @Test
  void hologramsAndMonitorItemsUseSmallerSamplesThanMainBlocks() {
    DisplayMetadataNormalizer.DisplayDimensions main =
        DisplayMetadataNormalizer.dimensionsForTags(Set.of(DisplayTags.DISPLAY_TAG, "storage"));
    DisplayMetadataNormalizer.DisplayDimensions monitorItem =
        DisplayMetadataNormalizer.dimensionsForTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.MONITOR_ITEM_TAG));
    DisplayMetadataNormalizer.DisplayDimensions hologram =
        DisplayMetadataNormalizer.dimensionsForTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.HOLOGRAM_TAG));

    assertTrue(monitorItem.width() < main.width());
    assertTrue(monitorItem.height() < main.height());
    assertTrue(hologram.width() < main.width());
    assertTrue(hologram.height() < main.height());
  }

  @Test
  void monitorTextUsesFlatDimensions() {
    DisplayMetadataNormalizer.DisplayDimensions dimensions =
        DisplayMetadataNormalizer.dimensionsForTags(
            Set.of(DisplayTags.DISPLAY_TAG, DisplayTags.MONITOR_TEXT_TAG));

    assertTrue(dimensions.width() > dimensions.height());
    assertTrue(dimensions.height() > 0.0f);
  }
}
