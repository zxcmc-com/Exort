package com.zxcmc.exort.core.items;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    // Paper/Purpur added ItemModel APIs in later 1.21.x.
    // Prefer looking up methods on the Bukkit interface to avoid CraftBukkit reflection
    // restrictions.
    ApplyResult r = invokePreferred(meta, "setItemModel", NamespacedKey.class, key);
    if (r.ok()) return r;

    r = invokePreferred(meta, "itemModel", NamespacedKey.class, key);
    if (r.ok()) return r;

    return ApplyResult.failure(r.error() != null ? r.error() : "item model API not available");
  }

  private static ApplyResult invokePreferred(
      Object target, String methodName, Class<?> paramType, Object arg) {
    try {
      Method m;
      try {
        // Avoid meta.getClass() first (CraftMetaItem) - can be affected by Paper reflection
        // rewriter.
        m = ItemMeta.class.getMethod(methodName, paramType);
      } catch (NoSuchMethodException ignored) {
        m = target.getClass().getMethod(methodName, paramType);
      }
      m.invoke(target, arg);
      return ApplyResult.success();
    } catch (NoSuchMethodException e) {
      return ApplyResult.failure(
          "method not found: " + methodName + "(" + paramType.getSimpleName() + ")");
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      return ApplyResult.failure(
          cause.getClass().getSimpleName()
              + ": "
              + (cause.getMessage() == null ? "" : cause.getMessage()));
    } catch (Throwable t) {
      return ApplyResult.failure(
          t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage()));
    }
  }
}
