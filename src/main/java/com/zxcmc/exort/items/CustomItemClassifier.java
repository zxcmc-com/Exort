package com.zxcmc.exort.items;

import com.zxcmc.exort.keys.PdcValueSanitizer;
import com.zxcmc.exort.keys.StorageKeys;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Classifies only item shapes that Exort currently creates and owns. */
public final class CustomItemClassifier {
  private static final String STORAGE_TYPE = "storage";

  private CustomItemClassifier() {}

  public static boolean isCustomItem(StorageKeys keys, ItemStack stack) {
    return recognizedType(keys, stack) != null;
  }

  public static boolean isType(StorageKeys keys, ItemStack stack, String expectedType) {
    if (expectedType == null) return false;
    String type = recognizedType(keys, stack);
    return type != null && type.equals(expectedType.toLowerCase(Locale.ROOT));
  }

  public static boolean hasDurableIdentity(StorageKeys keys, ItemStack stack) {
    if (keys == null || stack == null || !stack.hasItemMeta()) return false;
    var pdc = stack.getItemMeta().getPersistentDataContainer();
    return PdcValueSanitizer.uuidString(pdc.get(keys.storageId(), PersistentDataType.STRING))
            != null
        || PdcValueSanitizer.uuidString(pdc.get(keys.chunkLoaderId(), PersistentDataType.STRING))
            != null;
  }

  static String recognizedType(StorageKeys keys, ItemStack stack) {
    if (keys == null || stack == null || !stack.hasItemMeta()) return null;
    String raw =
        stack
            .getItemMeta()
            .getPersistentDataContainer()
            .get(keys.type(), PersistentDataType.STRING);
    if (raw == null || raw.isEmpty() || !raw.equals(raw.trim())) return null;
    String type = raw.toLowerCase(Locale.ROOT);
    if (!STORAGE_TYPE.equals(type) && CustomItemRegistry.fixedItem(type).isEmpty()) return null;
    Material expectedMaterial =
        CustomItemRegistry.WIRELESS_TERMINAL.id().equals(type) ? Material.SHIELD : Material.PAPER;
    return stack.getType() == expectedMaterial ? type : null;
  }
}
