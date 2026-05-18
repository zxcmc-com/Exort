package com.zxcmc.exort.core.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RecipeServiceTest {
  @Test
  void parseNamespacedKeyReturnsNullForBlankOrMalformedInput() {
    assertNull(RecipeService.parseNamespacedKey(null));
    assertNull(RecipeService.parseNamespacedKey(" "));
    assertNull(RecipeService.parseNamespacedKey("bad key"));
    assertNull(RecipeService.parseNamespacedKey("minecraft:bad key"));
  }

  @Test
  void parseNamespacedKeyNormalizesUppercaseInput() {
    var key = RecipeService.parseNamespacedKey("Minecraft:Stone");

    assertNotNull(key);
    assertEquals("minecraft", key.getNamespace());
    assertEquals("stone", key.getKey());
  }
}
