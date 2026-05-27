package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchQueryTest {
  @Test
  void emptyQueriesShareSingletonState() {
    assertSame(SearchQuery.empty(), SearchQuery.from(null));
    assertSame(SearchQuery.empty(), SearchQuery.from("   \n\t"));
    assertNull(SearchQuery.empty().displayText());
    assertTrue(SearchQuery.empty().tokens().isEmpty());
  }

  @Test
  void normalizesDisplayTextAndLowercaseTokensSeparately() {
    SearchQuery query = SearchQuery.from("  Diamond Sword  \n\n  GOLD  ");

    assertEquals("Diamond Sword\nGOLD", query.displayText());
    assertEquals(List.of("diamond sword", "gold"), query.tokens());
  }

  @Test
  void tokenListIsImmutable() {
    SearchQuery query = SearchQuery.from("stone");

    assertThrows(UnsupportedOperationException.class, () -> query.tokens().add("dirt"));
  }

  @Test
  void publicNormalizerKeepsLegacyRawTokenBehavior() {
    List<String> tokens = SortSearchHelper.normalizeSearchTokens(" One \n\n Two ");

    tokens.add("Three");

    assertEquals(List.of("One", "Two", "Three"), tokens);
  }
}
