package com.zxcmc.exort.items;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemModelUtil {
  private ItemModelUtil() {}

  public record ApplyResult(boolean ok, String error) {
    public static ApplyResult success() {
      return new ApplyResult(true, null);
    }

    public static ApplyResult failure(String error) {
      return new ApplyResult(false, error);
    }
  }

  public static boolean applyItemModel(ItemStack stack, String modelId) {
    if (stack == null) return false;
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) return false;
    boolean ok = applyItemModelDetailed(meta, modelId).ok();
    if (ok) {
      stack.setItemMeta(meta);
    }
    return ok;
  }

  public static boolean applyItemModel(ItemMeta meta, String modelId) {
    return applyItemModelDetailed(meta, modelId).ok();
  }

  public static ApplyResult applyItemModelDetailed(ItemMeta meta, String modelId) {
    if (meta == null) return ApplyResult.failure("meta is null");
    if (modelId == null || modelId.isBlank()) return ApplyResult.failure("modelId is blank");
    NamespacedKey key = NamespacedKey.fromString(modelId);
    if (key == null) return ApplyResult.failure("invalid NamespacedKey: '" + modelId + "'");

    try {
      meta.setItemModel(key);
      return ApplyResult.success();
    } catch (RuntimeException t) {
      return ApplyResult.failure(
          t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage()));
    }
  }
}
