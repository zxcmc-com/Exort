package com.zxcmc.exort.gui;

import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.i18n.StorageTierText;
import com.zxcmc.exort.items.CustomItemIdentity;
import com.zxcmc.exort.items.CustomItemRegistry;
import com.zxcmc.exort.items.StorageItemNameEditor;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.StorageTierResolver;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class SortSearchHelper {
  public record SortResult(List<StorageCache.StorageItem> ordered, List<String> order) {}

  private record ExortSearchNames(String primaryName, List<String> candidates) {}

  private static final CreativeTabOrder FALLBACK_CATEGORY_ORDER = CreativeTabOrder.fromData();

  private SortSearchHelper() {}

  public static List<String> normalizeSearchTokens(String query) {
    return new ArrayList<>(SearchQuery.normalizeRawTokens(query));
  }

  public static SortResult resolveOrder(
      List<StorageCache.StorageItem> items,
      SortMode sortMode,
      boolean sortFrozen,
      List<String> currentOrder,
      ItemNameService itemNames,
      boolean allowCategoryDuplicates) {
    return resolveOrder(
        items, sortMode, sortFrozen, currentOrder, itemNames, null, allowCategoryDuplicates);
  }

  public static SortResult resolveOrder(
      List<StorageCache.StorageItem> items,
      SortMode sortMode,
      boolean sortFrozen,
      List<String> currentOrder,
      ItemNameService itemNames,
      String language,
      boolean allowCategoryDuplicates) {
    return resolveOrder(
        items,
        sortMode,
        sortFrozen,
        currentOrder,
        itemNames,
        null,
        null,
        language,
        allowCategoryDuplicates);
  }

  public static SortResult resolveOrder(
      List<StorageCache.StorageItem> items,
      SortMode sortMode,
      boolean sortFrozen,
      List<String> currentOrder,
      ItemNameService itemNames,
      Lang lang,
      StorageKeys keys,
      String language,
      boolean allowCategoryDuplicates) {
    Map<String, StorageCache.StorageItem> byKey = new HashMap<>();
    for (StorageCache.StorageItem item : items) {
      byKey.put(item.key(), item);
    }
    if (sortFrozen) {
      if (currentOrder.isEmpty()) {
        List<StorageCache.StorageItem> sorted =
            sortItems(items, sortMode, itemNames, lang, keys, language, allowCategoryDuplicates);
        List<String> order =
            new ArrayList<>(sorted.stream().map(StorageCache.StorageItem::key).toList());
        return new SortResult(sorted, order);
      }
      List<StorageCache.StorageItem> ordered = new ArrayList<>();
      Set<String> keysInOrder = new HashSet<>();
      for (String key : currentOrder) {
        StorageCache.StorageItem item = byKey.get(key);
        if (item == null || item.amount() <= 0) continue;
        ordered.add(item);
        keysInOrder.add(key);
      }
      if (keysInOrder.size() < items.size()) {
        List<StorageCache.StorageItem> extra = new ArrayList<>();
        for (StorageCache.StorageItem item : items) {
          if (!keysInOrder.contains(item.key())) {
            extra.add(item);
          }
        }
        ordered.addAll(
            sortItems(extra, sortMode, itemNames, lang, keys, language, allowCategoryDuplicates));
      }
      return new SortResult(ordered, new ArrayList<>(currentOrder));
    }
    List<StorageCache.StorageItem> sorted =
        sortItems(items, sortMode, itemNames, lang, keys, language, allowCategoryDuplicates);
    List<String> order =
        new ArrayList<>(sorted.stream().map(StorageCache.StorageItem::key).toList());
    return new SortResult(sorted, order);
  }

  public static List<StorageCache.StorageItem> sortItems(
      List<StorageCache.StorageItem> items,
      SortMode sortMode,
      ItemNameService itemNames,
      boolean allowCategoryDuplicates) {
    return sortItems(items, sortMode, itemNames, null, allowCategoryDuplicates);
  }

  public static List<StorageCache.StorageItem> sortItems(
      List<StorageCache.StorageItem> items,
      SortMode sortMode,
      ItemNameService itemNames,
      String language,
      boolean allowCategoryDuplicates) {
    return sortItems(items, sortMode, itemNames, null, null, language, allowCategoryDuplicates);
  }

  public static List<StorageCache.StorageItem> sortItems(
      List<StorageCache.StorageItem> items,
      SortMode sortMode,
      ItemNameService itemNames,
      Lang lang,
      StorageKeys keys,
      String language,
      boolean allowCategoryDuplicates) {
    List<StorageCache.StorageItem> list = new ArrayList<>(items);
    Map<String, String> nameKeys = new HashMap<>(items.size());
    Map<String, String> idKeys = new HashMap<>(items.size());
    Map<String, Integer> categoryKeys = new HashMap<>(items.size());
    Map<String, CreativeTabOrder.Position> primaryPositions = new HashMap<>();
    Map<String, List<CreativeTabOrder.Position>> positionsCache = new HashMap<>();
    for (StorageCache.StorageItem item : items) {
      String key = item.key();
      nameKeys.put(key, sortNameKey(item.sample(), itemNames, lang, keys, language));
      idKeys.put(key, item.sample().getType().getKey().getKey());
      categoryKeys.put(key, categoryIndex(item.sample()));
    }
    if (sortMode == SortMode.AMOUNT) {
      list.sort(
          (a, b) -> {
            int cmp = Long.compare(b.amount(), a.amount());
            if (cmp != 0) return cmp;
            cmp = a.sample().getType().name().compareToIgnoreCase(b.sample().getType().name());
            if (cmp != 0) return cmp;
            return nameKeys.get(a.key()).compareToIgnoreCase(nameKeys.get(b.key()));
          });
      return list;
    }
    if (sortMode == SortMode.NAME) {
      list.sort(
          (a, b) -> {
            int cmp = nameKeys.get(a.key()).compareToIgnoreCase(nameKeys.get(b.key()));
            if (cmp != 0) return cmp;
            cmp = idKeys.get(a.key()).compareToIgnoreCase(idKeys.get(b.key()));
            if (cmp != 0) return cmp;
            return Long.compare(b.amount(), a.amount());
          });
      return list;
    }
    if (sortMode == SortMode.ID) {
      list.sort(
          (a, b) -> {
            int cmp = idKeys.get(a.key()).compareToIgnoreCase(idKeys.get(b.key()));
            if (cmp != 0) return cmp;
            return Long.compare(b.amount(), a.amount());
          });
      return list;
    }
    CreativeTabOrder order = CreativeTabOrder.get();
    if (order != null) {
      for (StorageCache.StorageItem item : items) {
        String key = item.key();
        primaryPositions.put(key, order.positionFor(item.sample()));
        if (allowCategoryDuplicates) {
          List<CreativeTabOrder.Position> positions = order.positionsFor(item.sample());
          if (positions != null && !positions.isEmpty()) {
            positionsCache.put(key, positions);
          }
        }
      }
    }
    if (order != null && allowCategoryDuplicates) {
      record Categorized(StorageCache.StorageItem item, CreativeTabOrder.Position pos) {}
      List<Categorized> categorized = new ArrayList<>();
      for (StorageCache.StorageItem item : list) {
        List<CreativeTabOrder.Position> positions = positionsCache.get(item.key());
        if (positions == null || positions.isEmpty()) {
          categorized.add(new Categorized(item, primaryPositions.get(item.key())));
          continue;
        }
        for (CreativeTabOrder.Position pos : positions) {
          categorized.add(new Categorized(item, pos));
        }
      }
      categorized.sort(
          (a, b) -> {
            int cmp = Integer.compare(a.pos.tabIndex(), b.pos.tabIndex());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.pos.indexInTab(), b.pos.indexInTab());
            if (cmp != 0) return cmp;
            cmp = nameKeys.get(a.item.key()).compareToIgnoreCase(nameKeys.get(b.item.key()));
            if (cmp != 0) return cmp;
            return Long.compare(b.item.amount(), a.item.amount());
          });
      List<StorageCache.StorageItem> expanded = new ArrayList<>(categorized.size());
      for (Categorized entry : categorized) {
        expanded.add(entry.item);
      }
      return expanded;
    }
    list.sort(
        (a, b) -> {
          int cmp;
          if (order != null) {
            CreativeTabOrder.Position posA = primaryPositions.get(a.key());
            CreativeTabOrder.Position posB = primaryPositions.get(b.key());
            cmp = Integer.compare(posA.tabIndex(), posB.tabIndex());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(posA.indexInTab(), posB.indexInTab());
          } else {
            cmp = Integer.compare(categoryKeys.get(a.key()), categoryKeys.get(b.key()));
          }
          if (cmp != 0) return cmp;
          cmp = Integer.compare(a.sample().getType().ordinal(), b.sample().getType().ordinal());
          if (cmp != 0) return cmp;
          cmp = nameKeys.get(a.key()).compareToIgnoreCase(nameKeys.get(b.key()));
          if (cmp != 0) return cmp;
          return Long.compare(b.amount(), a.amount());
        });
    return list;
  }

  public static boolean matchesQuery(
      ItemStack stack, List<String> searchTokens, ItemNameService itemNames) {
    return matchesQuery(stack, searchTokens, itemNames, null);
  }

  public static boolean matchesQuery(
      ItemStack stack, List<String> searchTokens, ItemNameService itemNames, String language) {
    return matchesQuery(stack, searchTokens, itemNames, null, null, language);
  }

  public static boolean matchesQuery(
      ItemStack stack,
      List<String> searchTokens,
      ItemNameService itemNames,
      Lang lang,
      StorageKeys keys,
      String language) {
    if (searchTokens == null || searchTokens.isEmpty()) return true;
    List<String> candidates = buildSearchCandidates(stack, itemNames, lang, keys, language);
    if (candidates.isEmpty()) return true;
    for (String token : searchTokens) {
      for (String candidate : candidates) {
        if (candidate.contains(token)) {
          return true;
        }
      }
    }
    return false;
  }

  public static String sortNameKey(ItemStack stack, ItemNameService itemNames) {
    return sortNameKey(stack, itemNames, null);
  }

  public static String sortNameKey(ItemStack stack, ItemNameService itemNames, String language) {
    return sortNameKey(stack, itemNames, null, null, language);
  }

  public static String sortNameKey(
      ItemStack stack, ItemNameService itemNames, Lang lang, StorageKeys keys, String language) {
    if (stack == null) return "";
    ItemMeta meta = stack.getItemMeta();
    if (meta != null) {
      if (meta.hasDisplayName()) {
        return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
      }
    }
    ExortSearchNames exortNames = exortSearchNames(stack, lang, keys, language);
    if (exortNames != null
        && exortNames.primaryName() != null
        && !exortNames.primaryName().isBlank()) {
      return exortNames.primaryName();
    }
    if (meta != null) {
      if (meta.hasItemName()) {
        return PlainTextComponentSerializer.plainText().serialize(meta.itemName());
      }
    }
    String resolved = itemNames != null ? itemNames.resolveName(stack, language) : null;
    if (resolved != null && !resolved.isBlank()) {
      return resolved;
    }
    return stack.getType().getKey().getKey();
  }

  public static List<String> buildSearchCandidates(ItemStack stack, ItemNameService itemNames) {
    return buildSearchCandidates(stack, itemNames, null);
  }

  public static List<String> buildSearchCandidates(
      ItemStack stack, ItemNameService itemNames, String language) {
    return buildSearchCandidates(stack, itemNames, null, null, language);
  }

  public static List<String> buildSearchCandidates(
      ItemStack stack, ItemNameService itemNames, Lang lang, StorageKeys keys, String language) {
    List<String> candidates = new ArrayList<>();
    if (stack == null) return candidates;
    ItemMeta meta = stack.getItemMeta();
    if (meta != null) {
      if (meta.hasDisplayName()) {
        String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        if (name != null && !name.isBlank()) {
          candidates.add(name.toLowerCase(Locale.ROOT));
        }
      }
    }
    ExortSearchNames exortNames = exortSearchNames(stack, lang, keys, language);
    if (exortNames != null) {
      for (String candidate : exortNames.candidates()) {
        addCandidate(candidates, candidate);
      }
    }
    if (meta != null) {
      if (meta.hasItemName()) {
        String name = PlainTextComponentSerializer.plainText().serialize(meta.itemName());
        if (name != null && !name.isBlank()) {
          candidates.add(name.toLowerCase(Locale.ROOT));
        }
      }
    }
    String dictionaryName =
        itemNames != null ? itemNames.resolveDictionaryName(stack, language) : null;
    if (dictionaryName != null && !dictionaryName.isBlank()) {
      candidates.add(dictionaryName.toLowerCase(Locale.ROOT));
    }
    String id = stack.getType().getKey().getKey();
    if (id != null && !id.isBlank()) {
      candidates.add(id.toLowerCase(Locale.ROOT));
    }
    return candidates;
  }

  private static ExortSearchNames exortSearchNames(
      ItemStack stack, Lang lang, StorageKeys keys, String language) {
    if (stack == null || lang == null || keys == null || !stack.hasItemMeta()) {
      return null;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return null;
    }
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    if (type == null || type.isBlank()) {
      return null;
    }
    String normalizedType = type.trim().toLowerCase(Locale.ROOT);
    if ("storage".equals(normalizedType)) {
      return storageSearchNames(pdc, lang, keys, language);
    }
    return CustomItemRegistry.fixedItem(normalizedType)
        .map(identity -> fixedItemSearchNames(identity, lang, language))
        .orElse(null);
  }

  private static ExortSearchNames fixedItemSearchNames(
      CustomItemIdentity identity, Lang lang, String language) {
    String localized = lang.trLanguage(language, identity.translationKey());
    List<String> candidates = new ArrayList<>();
    addCandidate(candidates, localized);
    addCandidate(candidates, identity.id());
    addCandidate(candidates, identity.namespacedId());
    return new ExortSearchNames(localized, List.copyOf(candidates));
  }

  private static ExortSearchNames storageSearchNames(
      PersistentDataContainer pdc, Lang lang, StorageKeys keys, String language) {
    String storageName = lang.trLanguage(language, "item.storage");
    List<String> candidates = new ArrayList<>();
    addCandidate(candidates, storageName);
    StorageTierResolver.Resolution resolution =
        StorageTierResolver.resolve(
                pdc.get(keys.storageTier(), PersistentDataType.STRING),
                pdc.get(keys.storageTierMaxItems(), PersistentDataType.LONG))
            .orElse(null);
    if (resolution != null) {
      StorageTier tier = resolution.tier();
      addCandidate(candidates, StorageTierText.tierNamePlain(lang, language, tier));
    }
    StorageItemNameEditor.displayName(keys, pdc).ifPresent(name -> addCandidate(candidates, name));
    addCandidate(candidates, "storage");
    addCandidate(candidates, "exort:storage");
    return new ExortSearchNames(storageName, List.copyOf(candidates));
  }

  private static void addCandidate(List<String> candidates, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    String normalized = value.toLowerCase(Locale.ROOT);
    if (!candidates.contains(normalized)) {
      candidates.add(normalized);
    }
  }

  public static int categoryIndex(ItemStack stack) {
    return FALLBACK_CATEGORY_ORDER.positionFor(stack).tabIndex();
  }
}
