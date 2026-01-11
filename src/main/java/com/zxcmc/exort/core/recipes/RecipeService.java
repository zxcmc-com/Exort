package com.zxcmc.exort.core.recipes;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecipeService {
    private final ExortPlugin plugin;
    private final CustomItems customItems;
    private final WirelessTerminalService wirelessService;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public RecipeService(ExortPlugin plugin, CustomItems customItems, WirelessTerminalService wirelessService) {
        this.plugin = plugin;
        this.customItems = customItems;
        this.wirelessService = wirelessService;
    }

    public void reload() {
        unregisterAll();
        if (!plugin.getConfig().getBoolean("recipes.enabled", true)) {
            plugin.getLogger().info("Recipes are disabled.");
            return;
        }
        File file = ensureFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int loaded = 0;
        int skipped = 0;

        var shaped = config.getConfigurationSection("shaped");
        if (shaped != null) {
            var result = registerShaped(shaped);
            loaded += result.loaded;
            skipped += result.skipped;
        }
        var shapeless = config.getConfigurationSection("shapeless");
        if (shapeless != null) {
            var result = registerShapeless(shapeless);
            loaded += result.loaded;
            skipped += result.skipped;
        }
        var smithing = config.getConfigurationSection("smithing");
        if (smithing != null) {
            var result = registerSmithing(smithing);
            loaded += result.loaded;
            skipped += result.skipped;
        }

        int disabled = disableRecipes(config.getStringList("disabled"));
        plugin.getLogger().info("Recipes loaded: " + loaded + ", skipped: " + skipped + ", disabled: " + disabled + ".");
    }

    public void unregisterAll() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
    }

    private File ensureFile() {
        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) {
            plugin.saveResource("recipes.yml", false);
        }
        return file;
    }

    private Result registerShaped(ConfigurationSection section) {
        int loaded = 0;
        int skipped = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection recipe = section.getConfigurationSection(id);
            if (recipe == null) {
                skipped++;
                continue;
            }
            ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"));
            if (resultItem == null) {
                skipped++;
                continue;
            }
            List<String> shape = recipe.getStringList("shape");
            if (shape == null || shape.isEmpty()) {
                logSkip(id, "missing shape");
                skipped++;
                continue;
            }
            List<String> normalizedShape = new ArrayList<>();
            java.util.Set<Character> used = new java.util.HashSet<>();
            boolean shapeOk = true;
            for (String line : shape) {
                if (line == null) {
                    shapeOk = false;
                    continue;
                }
                if (line.indexOf(' ') >= 0) {
                    shapeOk = false;
                    continue;
                }
                String normalized = line.replace('_', ' ');
                normalizedShape.add(normalized);
                for (int i = 0; i < normalized.length(); i++) {
                    char ch = normalized.charAt(i);
                    if (ch != ' ') {
                        used.add(ch);
                    }
                }
            }
            if (!shapeOk) {
                logSkip(id, "shape must use '_' for empty slots");
                skipped++;
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, id);
            ShapedRecipe shaped = new ShapedRecipe(key, resultItem);
            shaped.shape(normalizedShape.toArray(new String[0]));
            ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
            if (ingredients == null) {
                logSkip(id, "missing ingredients");
                skipped++;
                continue;
            }
            boolean ok = true;
            java.util.Set<Character> provided = new java.util.HashSet<>();
            for (String symbol : ingredients.getKeys(false)) {
                if (symbol == null || symbol.length() != 1) {
                    ok = false;
                    break;
                }
                char ch = symbol.charAt(0);
                if (ch == ' ' || ch == '_') {
                    continue;
                }
                String raw = ingredients.getString(symbol);
                RecipeChoice choice = resolveChoice(raw);
                if (choice == null) {
                    ok = false;
                    break;
                }
                shaped.setIngredient(ch, choice);
                provided.add(ch);
            }
            if (ok && !provided.containsAll(used)) {
                ok = false;
                for (Character ch : used) {
                    if (!provided.contains(ch)) {
                        logSkip(id, "missing ingredient for symbol '" + ch + "'");
                        break;
                    }
                }
            }
            if (!ok) {
                logSkip(id, "invalid ingredients");
                skipped++;
                continue;
            }
            Bukkit.addRecipe(shaped);
            registered.add(key);
            loaded++;
        }
        return new Result(loaded, skipped);
    }

    private Result registerShapeless(ConfigurationSection section) {
        int loaded = 0;
        int skipped = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection recipe = section.getConfigurationSection(id);
            if (recipe == null) {
                skipped++;
                continue;
            }
            ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"));
            if (resultItem == null) {
                skipped++;
                continue;
            }
            List<String> ingredients = recipe.getStringList("ingredients");
            if (ingredients == null || ingredients.isEmpty()) {
                logSkip(id, "missing ingredients");
                skipped++;
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, id);
            ShapelessRecipe shapeless = new ShapelessRecipe(key, resultItem);
            boolean ok = true;
            for (String raw : ingredients) {
                RecipeChoice choice = resolveChoice(raw);
                if (choice == null) {
                    ok = false;
                    break;
                }
                shapeless.addIngredient(choice);
            }
            if (!ok) {
                logSkip(id, "invalid ingredients");
                skipped++;
                continue;
            }
            Bukkit.addRecipe(shapeless);
            registered.add(key);
            loaded++;
        }
        return new Result(loaded, skipped);
    }

    private Result registerSmithing(ConfigurationSection section) {
        int loaded = 0;
        int skipped = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection recipe = section.getConfigurationSection(id);
            if (recipe == null) {
                skipped++;
                continue;
            }
            ItemStack resultItem = resolveResult(recipe.getConfigurationSection("result"));
            if (resultItem == null) {
                skipped++;
                continue;
            }
            RecipeChoice template = resolveChoice(recipe.getString("template"));
            RecipeChoice base = resolveChoice(recipe.getString("base"));
            RecipeChoice addition = resolveChoice(recipe.getString("addition"));
            if (template == null || base == null || addition == null) {
                logSkip(id, "invalid smithing ingredients");
                skipped++;
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, id);
            SmithingTransformRecipe smithing = new SmithingTransformRecipe(key, resultItem, template, base, addition);
            Bukkit.addRecipe(smithing);
            registered.add(key);
            loaded++;
        }
        return new Result(loaded, skipped);
    }

    private int disableRecipes(List<String> disabled) {
        if (disabled == null || disabled.isEmpty()) return 0;
        int removed = 0;
        for (String raw : disabled) {
            if (raw == null || raw.isBlank()) continue;
            NamespacedKey key = NamespacedKey.fromString(raw);
            if (key == null) {
                key = new NamespacedKey(plugin, raw);
            }
            if (Bukkit.removeRecipe(key)) {
                registered.remove(key);
                removed++;
            }
        }
        return removed;
    }

    private ItemStack resolveResult(ConfigurationSection section) {
        if (section == null) return null;
        String raw = section.getString("item");
        if (raw == null) return null;
        int amount = Math.max(1, section.getInt("amount", 1));
        ItemStack item = resolveExortItem(raw);
        if (item == null) {
            logSkip(raw, "result is not exort item");
            return null;
        }
        item.setAmount(amount);
        return item;
    }

    private RecipeChoice resolveChoice(String raw) {
        if (raw == null) return null;
        String id = raw.trim();
        if (id.isEmpty()) return null;
        if (id.startsWith("#")) {
            NamespacedKey key = NamespacedKey.fromString(id.substring(1));
            if (key == null) return null;
            Tag<Material> tag = Bukkit.getTag("items", key, Material.class);
            if (tag == null) return null;
            return new RecipeChoice.MaterialChoice(tag.getValues().toArray(new Material[0]));
        }
        if (id.toLowerCase(Locale.ROOT).startsWith("exort:")) {
            ItemStack exortItem = resolveExortItem(id);
            if (exortItem == null) return null;
            return new RecipeChoice.ExactChoice(exortItem);
        }
        Material material = resolveMaterial(id);
        if (material == null) return null;
        return new RecipeChoice.MaterialChoice(material);
    }

    private Material resolveMaterial(String raw) {
        String id = raw.trim();
        if (id.contains(":")) {
            NamespacedKey key = NamespacedKey.fromString(id);
            if (key != null && "minecraft".equalsIgnoreCase(key.getNamespace())) {
                id = key.getKey();
            }
        }
        return Material.matchMaterial(id);
    }

    private ItemStack resolveExortItem(String raw) {
        if (raw == null) return null;
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.startsWith("exort:")) {
            id = id.substring("exort:".length());
        }
        return switch (id) {
            case "wire" -> customItems.wireItem();
            case "terminal" -> customItems.terminalItem();
            case "crafting_terminal" -> customItems.craftingTerminalItem();
            case "monitor" -> customItems.monitorItem();
            case "import_bus" -> customItems.importBusItem();
            case "export_bus" -> customItems.exportBusItem();
            case "wireless_terminal" -> wirelessService != null ? wirelessService.create() : customItems.wirelessTerminalItem(null, 100);
            case "storage_core" -> customItems.storageCoreItem();
            default -> {
                if (id.startsWith("storage:")) {
                    String tier = id.substring("storage:".length());
                    var tierOpt = StorageTier.fromString(tier);
                    yield tierOpt.map(t -> customItems.storageItem(t, null)).orElse(null);
                }
                yield null;
            }
        };
    }

    private void logSkip(String id, String reason) {
        plugin.getLogger().warning("Skipped recipe '" + id + "': " + reason);
    }

    private record Result(int loaded, int skipped) {}
}
