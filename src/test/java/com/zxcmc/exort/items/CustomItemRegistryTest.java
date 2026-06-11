package com.zxcmc.exort.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CustomItemRegistryTest {
  @Test
  void fixedItemsExposeStableIdsAndTranslationKeys() {
    assertEquals(
        List.of(
            "storage_core",
            "terminal",
            "crafting_terminal",
            "monitor",
            "import_bus",
            "export_bus",
            "wire",
            "wireless_terminal"),
        CustomItemRegistry.fixedItemIds());
    assertEquals("item.storage_core", CustomItemRegistry.STORAGE_CORE.translationKey());
    assertEquals("exort:wire", CustomItemRegistry.WIRE.namespacedId());
    assertTrue(CustomItemRegistry.fixedItem("WIRE").isPresent());
  }
}
