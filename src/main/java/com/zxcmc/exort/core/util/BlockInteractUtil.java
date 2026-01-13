package com.zxcmc.exort.core.util;

import java.util.EnumSet;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bell;
import org.bukkit.block.data.type.Cake;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.Grindstone;
import org.bukkit.block.data.type.Jukebox;
import org.bukkit.block.data.type.Lectern;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.block.data.type.Switch;
import org.bukkit.inventory.InventoryHolder;

public final class BlockInteractUtil {
  private static final EnumSet<Material> MATERIALS =
      EnumSet.of(
          Material.CRAFTING_TABLE,
          Material.CARTOGRAPHY_TABLE,
          Material.SMITHING_TABLE,
          Material.LOOM,
          Material.STONECUTTER,
          Material.ANVIL,
          Material.CHIPPED_ANVIL,
          Material.DAMAGED_ANVIL,
          Material.ENCHANTING_TABLE,
          Material.BEACON,
          Material.CAULDRON,
          Material.WATER_CAULDRON,
          Material.LAVA_CAULDRON,
          Material.POWDER_SNOW_CAULDRON,
          Material.COMPOSTER,
          Material.RESPAWN_ANCHOR);

  private BlockInteractUtil() {}

  public static boolean isInteractable(Block block) {
    if (block == null) return false;
    if (block.getState() instanceof InventoryHolder) return true;
    var data = block.getBlockData();
    if (data instanceof Openable) return true;
    if (data instanceof Switch) return true;
    if (data instanceof Bed) return true;
    if (data instanceof Cake) return true;
    if (data instanceof Campfire) return true;
    if (data instanceof Comparator) return true;
    if (data instanceof Repeater) return true;
    if (data instanceof NoteBlock) return true;
    if (data instanceof Jukebox) return true;
    if (data instanceof Bell) return true;
    if (data instanceof Lectern) return true;
    if (data instanceof Grindstone) return true;
    return MATERIALS.contains(block.getType());
  }
}
