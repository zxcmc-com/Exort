package com.zxcmc.exort.gui;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CreativeTabOrder {
    public record Position(int tabIndex, int indexInTab) {}

    private static volatile CreativeTabOrder instance;

    private final Map<String, List<Position>> positions;
    private final List<String> tabIds;
    private final int customTabIndex;
    private final int unknownTabIndex;

    private CreativeTabOrder(Map<String, List<Position>> positions, List<String> tabIds) {
        this.positions = positions;
        this.tabIds = List.copyOf(tabIds);
        this.customTabIndex = tabIds.size();
        this.unknownTabIndex = tabIds.size() + 1;
    }

    public static void init(JavaPlugin plugin) {
        instance = load(plugin);
    }

    public static CreativeTabOrder get() {
        return instance;
    }

    public Position positionFor(ItemStack stack) {
        List<Position> all = positionsFor(stack);
        if (all.isEmpty()) {
            return new Position(unknownTabIndex, 0);
        }
        return all.get(0);
    }

    public List<Position> positionsFor(ItemStack stack) {
        if (stack == null) {
            return List.of(new Position(unknownTabIndex, 0));
        }
        if (isCustomItem(stack)) {
            return List.of(new Position(unknownTabIndex, 0));
        }
        String id = stack.getType().getKey().getKey();
        if (id == null) {
            return List.of(new Position(unknownTabIndex, 0));
        }
        List<Position> pos = positions.get(id);
        if (pos != null && !pos.isEmpty()) {
            return pos;
        }
        return List.of(new Position(unknownTabIndex, 0));
    }

    public String tabId(int index) {
        if (index >= 0 && index < tabIds.size()) {
            return tabIds.get(index);
        }
        if (index == customTabIndex) {
            return "exort:custom";
        }
        if (index == unknownTabIndex) {
            return "exort:other";
        }
        return null;
    }

    public int customTabIndex() {
        return customTabIndex;
    }

    public int unknownTabIndex() {
        return unknownTabIndex;
    }

    private static CreativeTabOrder load(JavaPlugin plugin) {
        try {
            Map<String, List<String>> tabs = CreativeTabData.load();
            Map<String, List<Position>> positions = new HashMap<>();
            List<String> tabIds = new ArrayList<>();
            int tabIndex = 0;
            for (Map.Entry<String, List<String>> entry : tabs.entrySet()) {
                String tabId = entry.getKey();
                if (tabId != null && (tabId.endsWith("search") || tabId.endsWith("hotbar"))) {
                    continue;
                }
                tabIds.add(tabId);
                List<String> raw = entry.getValue();
                if (raw != null) {
                    int index = 0;
                    for (String id : raw) {
                        if (id == null) {
                            continue;
                        }
                        if (id.startsWith("minecraft:")) {
                            id = id.substring("minecraft:".length());
                        }
                        List<Position> list = positions.computeIfAbsent(id, k -> new ArrayList<>());
                        list.add(new Position(tabIndex, index));
                        index++;
                    }
                }
                tabIndex++;
            }
            return new CreativeTabOrder(positions, tabIds);
        } catch (Exception e) {
            plugin.getLogger().warning("[Exort] Failed to load creative tab order: " + e.getMessage());
            return null;
        }
    }

    private static boolean isCustomItem(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return !pdc.getKeys().isEmpty();
    }
}
