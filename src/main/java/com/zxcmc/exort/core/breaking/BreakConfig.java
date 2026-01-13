package com.zxcmc.exort.core.breaking;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.configuration.file.FileConfiguration;

public final class BreakConfig {
  private final BreakSettings storage;
  private final BreakSettings terminal;
  private final BreakSettings monitor;
  private final BreakSettings bus;
  private final BreakSettings wire;

  public BreakConfig(
      BreakSettings storage,
      BreakSettings terminal,
      BreakSettings monitor,
      BreakSettings bus,
      BreakSettings wire) {
    this.storage = storage;
    this.terminal = terminal;
    this.monitor = monitor;
    this.bus = bus;
    this.wire = wire;
  }

  public BreakSettings storage() {
    return storage;
  }

  public BreakSettings terminal() {
    return terminal;
  }

  public BreakSettings monitor() {
    return monitor;
  }

  public BreakSettings bus() {
    return bus;
  }

  public BreakSettings wire() {
    return wire;
  }

  public static BreakConfig fromConfig(FileConfiguration config, Logger logger) {
    return new BreakConfig(
        load(config, logger, "break.storage", 20.0, defaultStorageTools()),
        load(config, logger, "break.terminal", 12.0, defaultTerminalTools()),
        load(config, logger, "break.monitor", 12.0, defaultTerminalTools()),
        load(config, logger, "break.bus", 12.0, defaultTerminalTools()),
        load(config, logger, "break.wire", 4.0, defaultWireTools()));
  }

  private static BreakSettings load(
      FileConfiguration config,
      Logger logger,
      String path,
      double defaultHardness,
      Set<Material> defaults) {
    double hardness = config.getDouble(path + ".hardness", defaultHardness);
    List<String> toolNames = config.getStringList(path + ".tools");
    Set<Material> tools = new HashSet<>();
    if (toolNames == null || toolNames.isEmpty()) {
      tools.addAll(defaults);
    } else {
      for (String raw : toolNames) {
        if (raw == null || raw.isBlank()) continue;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        Set<Material> category = toolCategory(normalized);
        if (!category.isEmpty()) {
          tools.addAll(category);
          continue;
        }
        Material mat = parseMaterial(raw);
        if (mat == null) {
          if (logger != null) {
            logger.warning("Unknown break tool entry '" + raw + "' at " + path + ".tools");
          }
          continue;
        }
        tools.add(mat);
      }
    }
    return new BreakSettings(hardness, tools);
  }

  private static Set<Material> toolCategory(String name) {
    return switch (name) {
      case "pickaxe" -> Tag.ITEMS_PICKAXES.getValues();
      case "axe" -> Tag.ITEMS_AXES.getValues();
      case "shovel" -> Tag.ITEMS_SHOVELS.getValues();
      case "hoe" -> Tag.ITEMS_HOES.getValues();
      case "sword" -> Tag.ITEMS_SWORDS.getValues();
      case "mace" -> singleTool(Material.MACE);
      case "trident" -> singleTool(Material.TRIDENT);
      case "bow" -> singleTool(Material.BOW);
      case "crossbow" -> singleTool(Material.CROSSBOW);
      case "shears" -> singleTool(Material.SHEARS);
      default -> Set.of();
    };
  }

  private static Set<Material> singleTool(Material material) {
    return material != null ? Set.of(material) : Set.of();
  }

  private static Material parseMaterial(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    String upper = trimmed.toUpperCase(Locale.ROOT);
    if (!upper.contains(":")) {
      try {
        return Material.valueOf(upper);
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    NamespacedKey key = NamespacedKey.fromString(trimmed.toLowerCase(Locale.ROOT));
    if (key != null) {
      Material byKey = Registry.MATERIAL.get(key);
      if (byKey != null) {
        return byKey;
      }
    }
    int colon = upper.indexOf(':');
    if (colon >= 0 && colon + 1 < upper.length()) {
      String fallback = upper.substring(colon + 1);
      try {
        return Material.valueOf(fallback);
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    return null;
  }

  private static Set<Material> defaultStorageTools() {
    return Set.of(
        Material.WOODEN_PICKAXE,
        Material.STONE_PICKAXE,
        Material.COPPER_PICKAXE,
        Material.IRON_PICKAXE,
        Material.DIAMOND_PICKAXE,
        Material.NETHERITE_PICKAXE,
        Material.GOLDEN_PICKAXE);
  }

  private static Set<Material> defaultTerminalTools() {
    return Set.of(
        Material.WOODEN_PICKAXE,
        Material.STONE_PICKAXE,
        Material.COPPER_PICKAXE,
        Material.IRON_PICKAXE,
        Material.DIAMOND_PICKAXE,
        Material.NETHERITE_PICKAXE,
        Material.GOLDEN_PICKAXE,
        Material.WOODEN_AXE,
        Material.STONE_AXE,
        Material.COPPER_AXE,
        Material.IRON_AXE,
        Material.DIAMOND_AXE,
        Material.NETHERITE_AXE,
        Material.GOLDEN_AXE);
  }

  private static Set<Material> defaultWireTools() {
    return Set.of(
        Material.WOODEN_PICKAXE,
        Material.STONE_PICKAXE,
        Material.COPPER_PICKAXE,
        Material.IRON_PICKAXE,
        Material.DIAMOND_PICKAXE,
        Material.NETHERITE_PICKAXE,
        Material.GOLDEN_PICKAXE,
        Material.WOODEN_AXE,
        Material.STONE_AXE,
        Material.COPPER_AXE,
        Material.IRON_AXE,
        Material.DIAMOND_AXE,
        Material.NETHERITE_AXE,
        Material.GOLDEN_AXE,
        Material.WOODEN_SWORD,
        Material.STONE_SWORD,
        Material.COPPER_SWORD,
        Material.IRON_SWORD,
        Material.DIAMOND_SWORD,
        Material.NETHERITE_SWORD,
        Material.GOLDEN_SWORD);
  }
}
