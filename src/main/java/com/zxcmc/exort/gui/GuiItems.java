package com.zxcmc.exort.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.zxcmc.exort.core.items.ItemModelUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.UUID;

public final class GuiItems {
    public static final String RESOURCE_BUTTON_MODEL = "exort:none";
    public static final String HEAD_NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTdiMDNiNzFkM2Y4NjIyMGVmMTIyZjk4MzFhNzI2ZWIyYjI4MzMxOWM3YjYyZTdkY2QyZDY0ZDk2ODIifX19";
    public static final String HEAD_PREV_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDgzNDhhYTc3ZjlmYjJiOTFlZWY2NjJiNWM4MWI1Y2EzMzVkZGVlMWI5MDVmM2E4YjkyMDk1ZDBhMWYxNDEifX19";
    public static final String HEAD_SORT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjdkYzNlMjlhMDkyM2U1MmVjZWU2YjRjOWQ1MzNhNzllNzRiYjZiZWQ1NDFiNDk1YTEzYWJkMzU5NjI3NjUzIn19fQ==";
    public static final String HEAD_INFO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTZkNTU0YWQ1ZTBkYzYwMWVmYmI5MjVkMTM0MjRjY2VhNTMyYzgzMWE5MGI5Y2E3M2Q1ZTkzYWI2ZGJjNWRhZiJ9fX0=";
    public static final String HEAD_SEARCH = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMDlhNTJjYjUwOTkyZDgzYzU1OTlmZDZlNDFhNmNlOTljZjdmMWU2MjAzNjExOTYzZGMyYzJmZGEwYjU1NTgzIn19fQ==";
    public static final String HEAD_CRAFT_STORAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWYxMzNlOTE5MTlkYjBhY2VmZGMyNzJkNjdmZDg3YjRiZTg4ZGM0NGE5NTg5NTg4MjQ0NzRlMjFlMDZkNTNlNiJ9fX0=";
    public static final String HEAD_CRAFT_PLAYER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzkxMmQ0NWIxYzc4Y2MyMjQ1MjcyM2VlNjZiYTJkMTU3NzdjYzI4ODU2OGQ2YzFiNjJhNTQ1YjI5YzcxODcifX19";

    private GuiItems() {
    }

    private static final ItemStack FILLER_GLASS = createFiller(true);
    private static final ItemStack FILLER_PAPER = createFiller(false);

    public static ItemStack button(Material vanillaMaterial, String name, String loreLine, boolean useFillers) {
        Component nameComp = Component.text(name).decoration(TextDecoration.ITALIC, false);
        List<Component> lore = loreLine == null
                ? null
                : List.of(Component.text(loreLine).decoration(TextDecoration.ITALIC, false));
        return button(vanillaMaterial, nameComp, lore, useFillers);
    }

    public static ItemStack button(Material vanillaMaterial, Component name, List<Component> lore, boolean useFillers) {
        return headButton(null, vanillaMaterial, name, lore, useFillers);
    }

    public static ItemStack pagePrev(String name, String pageInfo, boolean useFillers) {
        Component title = Component.text(name).decoration(TextDecoration.ITALIC, false);
        List<Component> lore = List.of(Component.text(pageInfo).decoration(TextDecoration.ITALIC, false));
        return headButton(HEAD_PREV_PAGE, Material.ARROW, title, lore, useFillers);
    }

    public static ItemStack pagePrev(String name, List<Component> lore, boolean useFillers) {
        Component title = Component.text(name).decoration(TextDecoration.ITALIC, false);
        return headButton(HEAD_PREV_PAGE, Material.ARROW, title, lore, useFillers);
    }

    public static ItemStack pageNext(String name, String pageInfo, boolean useFillers) {
        Component title = Component.text(name).decoration(TextDecoration.ITALIC, false);
        List<Component> lore = List.of(Component.text(pageInfo).decoration(TextDecoration.ITALIC, false));
        return headButton(HEAD_NEXT_PAGE, Material.ARROW, title, lore, useFillers);
    }

    public static ItemStack pageNext(String name, List<Component> lore, boolean useFillers) {
        Component title = Component.text(name).decoration(TextDecoration.ITALIC, false);
        return headButton(HEAD_NEXT_PAGE, Material.ARROW, title, lore, useFillers);
    }

    public static ItemStack sortButton(Component name, List<Component> lore, boolean useFillers) {
        return headButton(HEAD_SORT, Material.HOPPER, name, lore, useFillers);
    }

    public static ItemStack infoButton(Component name, List<Component> lore, boolean useFillers) {
        return headButton(HEAD_INFO, Material.BOOK, name, lore, useFillers);
    }

    public static ItemStack searchButton(Component name, List<Component> lore, boolean useFillers) {
        return headButton(HEAD_SEARCH, Material.COMPASS, name, lore, useFillers);
    }

    public static ItemStack infoErrorButton(Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.itemName(name);
            }
            if (lore != null) {
                meta.lore(lore);
            }
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setHideTooltip(lore == null || lore.isEmpty());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack craftStorageButton(Component name, List<Component> lore, boolean useFillers) {
        return headButton(HEAD_CRAFT_STORAGE, Material.CRAFTING_TABLE, name, lore, useFillers);
    }

    public static ItemStack craftPlayerButton(Component name, List<Component> lore, boolean useFillers) {
        return headButton(HEAD_CRAFT_PLAYER, Material.CRAFTING_TABLE, name, lore, useFillers);
    }

    public static ItemStack filler(boolean useFillers) {
        return (useFillers ? FILLER_GLASS : FILLER_PAPER).clone();
    }

    private static ItemStack createFiller(boolean useFillers) {
        Material base = useFillers ? Material.BLACK_STAINED_GLASS_PANE : Material.PAPER;
        ItemStack item = new ItemStack(base);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.itemName(Component.text("").decoration(TextDecoration.ITALIC, false));
            meta.setHideTooltip(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.setHideTooltip(true);
            applyResourceModel(meta, useFillers);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void applyResourceModel(ItemMeta meta, boolean useFillers) {
        if (!useFillers) {
            ItemModelUtil.applyItemModel(meta, RESOURCE_BUTTON_MODEL);
        }
    }

    private static ItemStack headButton(String texture, Material vanillaFallback, Component name, List<Component> lore, boolean useFillers) {
        Material base = useFillers
                ? (texture == null ? vanillaFallback : Material.PLAYER_HEAD)
                : Material.PAPER;
        ItemStack item = new ItemStack(base);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.itemName(name);
            }
            if (lore != null) {
                meta.lore(lore);
            }
            if (useFillers && texture != null && meta instanceof SkullMeta skullMeta) {
                applyHeadTexture(skullMeta, texture);
                // Ensure name shows for player heads (some clients ignore item_name here)
                if (name != null) {
                    meta.displayName(name);
                }
            }
            applyResourceModel(meta, useFillers);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void applyHeadTexture(SkullMeta meta, String textureValue) {
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", textureValue));
        meta.setPlayerProfile(profile);
    }
}
