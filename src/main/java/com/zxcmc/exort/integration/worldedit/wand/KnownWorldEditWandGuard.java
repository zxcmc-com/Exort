package com.zxcmc.exort.integration.worldedit.wand;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class KnownWorldEditWandGuard implements WorldEditWandGuard {
  private static final String BRIDGE_CLASS =
      "com.zxcmc.exort.integration.worldedit.wand.WorldEditWandBridge";
  private static final String WORLDEDIT_CLASS = "com.sk89q.worldedit.WorldEdit";
  private static final String WORLDEDIT_PLUGIN = "WorldEdit";
  private static final String FAWE_PLUGIN = "FastAsyncWorldEdit";
  private static final long FALLBACK_RELOAD_MILLIS = 5_000L;

  private final ClassLoader classLoader;
  private Method bridgeMethod;
  private boolean bridgeUnavailable;
  private long fallbackLoadedAt;
  private Set<Material> fallbackSelectionWands = EnumSet.of(Material.WOODEN_AXE);
  private Set<Material> fallbackNavigationWands = EnumSet.of(Material.COMPASS);

  public KnownWorldEditWandGuard(Plugin plugin) {
    this.classLoader =
        plugin == null
            ? KnownWorldEditWandGuard.class.getClassLoader()
            : plugin.getClass().getClassLoader();
  }

  @Override
  public boolean isWorldEditWand(Player player, ItemStack item) {
    if (player == null || item == null || item.getType().isAir()) return false;
    if (worldEditPlugin() == null) return false;

    Boolean bridgeResult = detectWithBridge(player, item);
    if (bridgeResult != null) {
      return bridgeResult;
    }

    Material material = item.getType();
    refreshFallbackWands();
    if (fallbackSelectionWands.contains(material)
        && player.hasPermission("worldedit.selection.pos")) {
      return true;
    }
    return fallbackNavigationWands.contains(material)
        && (player.hasPermission("worldedit.navigation.thru.tool")
            || player.hasPermission("worldedit.navigation.jumpto.tool"));
  }

  private Boolean detectWithBridge(Player player, ItemStack item) {
    if (bridgeUnavailable || !isClassAvailable(WORLDEDIT_CLASS)) {
      return null;
    }
    try {
      if (bridgeMethod == null) {
        Class<?> bridgeClass = Class.forName(BRIDGE_CLASS, true, classLoader);
        bridgeMethod =
            bridgeClass.getDeclaredMethod("detectWorldEditWand", Player.class, ItemStack.class);
        bridgeMethod.setAccessible(true);
      }
      Object result = bridgeMethod.invoke(null, player, item);
      return result instanceof Boolean value ? value : null;
    } catch (ReflectiveOperationException | LinkageError e) {
      bridgeUnavailable = true;
      return null;
    }
  }

  private void refreshFallbackWands() {
    long now = System.currentTimeMillis();
    if (now - fallbackLoadedAt < FALLBACK_RELOAD_MILLIS) return;
    fallbackLoadedAt = now;

    Set<Material> selection = EnumSet.of(Material.WOODEN_AXE);
    Set<Material> navigation = EnumSet.of(Material.COMPASS);
    Plugin worldEdit = worldEditPlugin();
    if (worldEdit != null) {
      YamlConfiguration config =
          YamlConfiguration.loadConfiguration(
              new java.io.File(worldEdit.getDataFolder(), "config.yml"));
      Material configuredSelection = materialFromWorldEditItem(config.getString("wand-item"));
      Material configuredNavigation =
          materialFromWorldEditItem(config.getString("navigation-wand.item"));
      if (configuredSelection != null) {
        selection.add(configuredSelection);
      }
      if (configuredNavigation != null) {
        navigation.add(configuredNavigation);
      }
    }
    fallbackSelectionWands = selection;
    fallbackNavigationWands = navigation;
  }

  static Material materialFromWorldEditItem(String itemId) {
    if (itemId == null || itemId.isBlank()) return null;
    String normalized = itemId.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("minecraft:")) {
      normalized = normalized.substring("minecraft:".length());
    }
    return Material.matchMaterial(normalized);
  }

  private Plugin worldEditPlugin() {
    Plugin worldEdit = Bukkit.getPluginManager().getPlugin(WORLDEDIT_PLUGIN);
    return worldEdit == null ? Bukkit.getPluginManager().getPlugin(FAWE_PLUGIN) : worldEdit;
  }

  private boolean isClassAvailable(String className) {
    try {
      Class.forName(className, false, classLoader);
      return true;
    } catch (ClassNotFoundException | LinkageError ignored) {
      return false;
    }
  }
}
