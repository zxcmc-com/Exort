package com.zxcmc.exort.gui;

import com.zxcmc.exort.core.i18n.ItemNameService;
import com.zxcmc.exort.storage.StorageCache;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SortSearchHelper {
    public record SortResult(List<StorageCache.StorageItem> ordered, List<String> order) {}
    private SortSearchHelper() {
    }

    public static List<String> normalizeSearchTokens(String query) {
        if (query == null) return List.of();
        String normalized = query.trim();
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
        return tokens;
    }

    public static SortResult resolveOrder(List<StorageCache.StorageItem> items, SortMode sortMode, boolean sortFrozen, List<String> currentOrder, ItemNameService itemNames, boolean allowCategoryDuplicates) {
        Map<String, StorageCache.StorageItem> byKey = new HashMap<>();
        for (StorageCache.StorageItem item : items) {
            byKey.put(item.key(), item);
        }
        if (sortFrozen) {
            if (currentOrder.isEmpty()) {
                List<StorageCache.StorageItem> sorted = sortItems(items, sortMode, itemNames, allowCategoryDuplicates);
                List<String> order = new ArrayList<>(sorted.stream().map(StorageCache.StorageItem::key).toList());
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
                ordered.addAll(sortItems(extra, sortMode, itemNames, allowCategoryDuplicates));
            }
            return new SortResult(ordered, new ArrayList<>(currentOrder));
        }
        List<StorageCache.StorageItem> sorted = sortItems(items, sortMode, itemNames, allowCategoryDuplicates);
        List<String> order = new ArrayList<>(sorted.stream().map(StorageCache.StorageItem::key).toList());
        return new SortResult(sorted, order);
    }

    public static List<StorageCache.StorageItem> sortItems(List<StorageCache.StorageItem> items, SortMode sortMode, ItemNameService itemNames, boolean allowCategoryDuplicates) {
        List<StorageCache.StorageItem> list = new ArrayList<>(items);
        Map<String, String> nameKeys = new HashMap<>(items.size());
        Map<String, String> idKeys = new HashMap<>(items.size());
        Map<String, Integer> categoryKeys = new HashMap<>(items.size());
        Map<String, CreativeTabOrder.Position> primaryPositions = new HashMap<>();
        Map<String, List<CreativeTabOrder.Position>> positionsCache = new HashMap<>();
        for (StorageCache.StorageItem item : items) {
            String key = item.key();
            nameKeys.put(key, sortNameKey(item.sample(), itemNames));
            idKeys.put(key, item.sample().getType().getKey().getKey());
            categoryKeys.put(key, categoryIndex(item.sample()));
        }
        if (sortMode == SortMode.AMOUNT) {
            list.sort((a, b) -> {
                int cmp = Long.compare(b.amount(), a.amount());
                if (cmp != 0) return cmp;
                cmp = a.sample().getType().name().compareToIgnoreCase(b.sample().getType().name());
                if (cmp != 0) return cmp;
                return nameKeys.get(a.key()).compareToIgnoreCase(nameKeys.get(b.key()));
            });
            return list;
        }
        if (sortMode == SortMode.NAME) {
            list.sort((a, b) -> {
                int cmp = nameKeys.get(a.key()).compareToIgnoreCase(nameKeys.get(b.key()));
                if (cmp != 0) return cmp;
                cmp = idKeys.get(a.key()).compareToIgnoreCase(idKeys.get(b.key()));
                if (cmp != 0) return cmp;
                return Long.compare(b.amount(), a.amount());
            });
            return list;
        }
        if (sortMode == SortMode.ID) {
            list.sort((a, b) -> {
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
            categorized.sort((a, b) -> {
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
        list.sort((a, b) -> {
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

    public static boolean matchesQuery(ItemStack stack, List<String> searchTokens, ItemNameService itemNames) {
        if (searchTokens == null || searchTokens.isEmpty()) return true;
        List<String> candidates = buildSearchCandidates(stack, itemNames);
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
        if (stack == null) return "";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            }
            if (meta.hasItemName()) {
                return PlainTextComponentSerializer.plainText().serialize(meta.itemName());
            }
        }
        String resolved = itemNames.resolveName(stack);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return stack.getType().getKey().getKey();
    }

    public static List<String> buildSearchCandidates(ItemStack stack, ItemNameService itemNames) {
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
            if (meta.hasItemName()) {
                String name = PlainTextComponentSerializer.plainText().serialize(meta.itemName());
                if (name != null && !name.isBlank()) {
                    candidates.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        String dictionaryName = itemNames.resolveDictionaryName(stack);
        if (dictionaryName != null && !dictionaryName.isBlank()) {
            candidates.add(dictionaryName.toLowerCase(Locale.ROOT));
        }
        String id = stack.getType().getKey().getKey();
        if (id != null && !id.isBlank()) {
            candidates.add(id.toLowerCase(Locale.ROOT));
        }
        return candidates;
    }

    public static int categoryIndex(ItemStack stack) {
        if (stack == null) return 10;
        if (isCustomItem(stack)) {
            return 9;
        }
        var category = stack.getType().getCreativeCategory();
        if (category == null) {
            return 10;
        }
        return switch (category) {
            case BUILDING_BLOCKS -> 0;
            case DECORATIONS -> 1;
            case REDSTONE -> 2;
            case TRANSPORTATION -> 3;
            case MISC -> 4;
            case FOOD -> 5;
            case TOOLS -> 6;
            case COMBAT -> 7;
            case BREWING -> 8;
        };
    }


    private static boolean isCustomItem(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return !pdc.getKeys().isEmpty();
    }
}
