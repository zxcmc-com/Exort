package com.zxcmc.exort.integration.worldedit.wand;

import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.tool.DistanceWand;
import com.sk89q.worldedit.command.tool.NavigationWand;
import com.sk89q.worldedit.command.tool.SelectionWand;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldedit.world.item.ItemTypes;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

final class WorldEditWandBridge {
  private WorldEditWandBridge() {}

  static Boolean detectWorldEditWand(org.bukkit.entity.Player player, ItemStack item) {
    if (player == null || item == null || item.getType().isAir()) return false;
    String itemId = item.getType().getKey().asString();
    ItemType itemType = ItemTypes.get(itemId);
    if (itemType == null) return false;

    Actor actor = wrapBukkitActor(player);
    if (actor == null) {
      return isConfiguredWand(player, itemId);
    }

    LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
    Tool tool = session.getTool(itemType);
    if (tool == null || !isWandTool(tool)) {
      return false;
    }
    try {
      return tool.canUse(actor);
    } catch (RuntimeException ignored) {
      return true;
    }
  }

  private static boolean isConfiguredWand(org.bukkit.entity.Player player, String itemId) {
    LocalConfiguration config = WorldEdit.getInstance().getConfiguration();
    if (itemId.equalsIgnoreCase(config.wandItem)) {
      return player.hasPermission("worldedit.selection.pos");
    }
    return itemId.equalsIgnoreCase(config.navigationWand)
        && (player.hasPermission("worldedit.navigation.thru.tool")
            || player.hasPermission("worldedit.navigation.jumpto.tool"));
  }

  private static boolean isWandTool(Tool tool) {
    return tool instanceof SelectionWand
        || tool instanceof NavigationWand
        || tool instanceof DistanceWand
        || isWorldEditWandByName(tool);
  }

  private static boolean isWorldEditWandByName(Tool tool) {
    if (tool == null) return false;
    Class<?> type = tool.getClass();
    return type.getName().startsWith("com.sk89q.worldedit.")
        && type.getSimpleName().endsWith("Wand");
  }

  private static Actor wrapBukkitActor(org.bukkit.entity.Player player) {
    Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
    if (worldEdit == null) {
      worldEdit = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
    }
    if (worldEdit == null) return null;
    try {
      Method method = worldEdit.getClass().getMethod("wrapPlayer", org.bukkit.entity.Player.class);
      Object actor = method.invoke(worldEdit, player);
      if (actor instanceof Actor wrapped) {
        return wrapped;
      }
    } catch (Exception ignored) {
      // Fall back to wrapCommandSender below.
    }
    try {
      Method method =
          worldEdit
              .getClass()
              .getMethod("wrapCommandSender", org.bukkit.command.CommandSender.class);
      Object actor = method.invoke(worldEdit, player);
      return actor instanceof Actor wrapped ? wrapped : null;
    } catch (Exception ignored) {
      return null;
    }
  }
}
