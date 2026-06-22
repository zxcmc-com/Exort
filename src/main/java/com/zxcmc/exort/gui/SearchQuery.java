package com.zxcmc.exort.gui;

import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

record SearchQuery(String displayText, List<String> tokens) {
  private static final SearchQuery EMPTY = new SearchQuery(null, List.of());

  SearchQuery {
    tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
    if (tokens.isEmpty()) {
      displayText = null;
    } else {
      displayText = Objects.requireNonNull(displayText, "displayText");
    }
  }

  static SearchQuery empty() {
    return EMPTY;
  }

  static SearchQuery from(String rawQuery) {
    List<String> normalizedTokens = normalizeRawTokens(rawQuery);
    if (normalizedTokens.isEmpty()) {
      return EMPTY;
    }
    List<String> lowered = new ArrayList<>(normalizedTokens.size());
    for (String token : normalizedTokens) {
      lowered.add(token.toLowerCase(Locale.ROOT));
    }
    return new SearchQuery(String.join("\n", normalizedTokens), lowered);
  }

  static List<String> normalizeRawTokens(String rawQuery) {
    if (rawQuery == null) return List.of();
    String normalized = rawQuery.trim();
    if (normalized.isEmpty()) return List.of();
    String[] lines = normalized.split("\\R");
    List<String> tokens = new ArrayList<>();
    for (String line : lines) {
      if (line == null) continue;
      String token = line.trim();
      if (!token.isEmpty()) {
        tokens.add(token);
      }
    }
    return List.copyOf(tokens);
  }

  boolean isEmpty() {
    return tokens.isEmpty();
  }

  boolean matches(ItemStack stack, ItemNameService itemNames, String language) {
    return SortSearchHelper.matchesQuery(stack, tokens, itemNames, language);
  }

  boolean matches(
      ItemStack stack, ItemNameService itemNames, Lang lang, StorageKeys keys, String language) {
    return SortSearchHelper.matchesQuery(stack, tokens, itemNames, lang, keys, language);
  }

  boolean matchesCached(
      StorageCache.StorageItem item,
      Map<String, List<String>> candidatesCache,
      ItemNameService itemNames,
      String language) {
    return matchesCached(item, candidatesCache, itemNames, null, null, language);
  }

  boolean matchesCached(
      StorageCache.StorageItem item,
      Map<String, List<String>> candidatesCache,
      ItemNameService itemNames,
      Lang lang,
      StorageKeys keys,
      String language) {
    if (tokens.isEmpty()) return true;
    List<String> candidates =
        candidatesCache.computeIfAbsent(
            item.key(),
            key ->
                SortSearchHelper.buildSearchCandidates(
                    item.sample(), itemNames, lang, keys, language));
    if (candidates.isEmpty()) return true;
    for (String token : tokens) {
      for (String candidate : candidates) {
        if (candidate.contains(token)) {
          return true;
        }
      }
    }
    return false;
  }
}
