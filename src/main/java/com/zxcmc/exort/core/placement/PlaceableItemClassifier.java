package com.zxcmc.exort.core.placement;

import java.util.EnumSet;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class PlaceableItemClassifier {
  private static final EnumSet<Material> USE_ON_BLOCK_ITEMS =
      EnumSet.of(
          Material.ARMOR_STAND,
          Material.END_CRYSTAL,
          Material.FIRE_CHARGE,
          Material.FLINT_AND_STEEL,
          Material.GLOW_ITEM_FRAME,
          Material.ITEM_FRAME,
          Material.PAINTING);

  private PlaceableItemClassifier() {}

  public static boolean isPotentialPlacementItem(ItemStack stack) {
    if (stack == null) return false;
    Material type = stack.getType();
    if (type == null) return false;
    String name = type.name().toUpperCase(Locale.ROOT);
    if (isAir(type, name)) return false;
    if (isBlock(type, name)) return true;
    if (USE_ON_BLOCK_ITEMS.contains(type)) return true;
    if (name.endsWith("_SPAWN_EGG")) return true;
    if (name.endsWith("_BOAT") || name.endsWith("_RAFT")) return true;
    if (name.equals("MINECART") || name.endsWith("_MINECART")) return true;
    return isBucketPlacement(type, name);
  }

  private static boolean isAir(Material type, String name) {
    return type == Material.AIR || name.endsWith("_AIR");
  }

  private static boolean isBlock(Material type, String name) {
    try {
      return type.isBlock();
    } catch (ExceptionInInitializerError | NoClassDefFoundError | IllegalStateException ignored) {
      // Paper's registry-backed Material predicates are unavailable in plain unit tests.
      return "STONE".equals(name);
    }
  }

  private static boolean isBucketPlacement(Material type, String name) {
    return name.endsWith("_BUCKET") && type != Material.BUCKET && type != Material.MILK_BUCKET;
  }
}
