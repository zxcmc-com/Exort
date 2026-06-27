package com.zxcmc.exort.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomItemRegistryTest {
  @Test
  void fixedItemsUseUniqueIdsAndConventionalKeys() {
    Set<String> ids = new HashSet<>();
    for (CustomItemIdentity item : CustomItemRegistry.fixedItems()) {
      assertTrue(ids.add(item.id()), "duplicate id " + item.id());
      assertEquals("item." + item.id(), item.translationKey());
      assertEquals("exort:" + item.id(), item.namespacedId());
    }
    assertTrue(CustomItemRegistry.fixedItem("WIRE").isPresent());
  }
}
