package com.zxcmc.exort.items;

import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageDisplayName;
import com.zxcmc.exort.storage.StorageNameNormalizer;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class StorageItemNameEditor {
  private StorageItemNameEditor() {}

  public static boolean isStorageItem(StorageKeys keys, ItemStack stack) {
    if (keys == null || stack == null || !stack.hasItemMeta()) {
      return false;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return false;
    }
    String type = meta.getPersistentDataContainer().get(keys.type(), PersistentDataType.STRING);
    return "storage".equalsIgnoreCase(type);
  }

  public static Optional<String> displayName(StorageKeys keys, ItemStack stack) {
    if (!isStorageItem(keys, stack)) {
      return Optional.empty();
    }
    return displayName(keys, stack.getItemMeta().getPersistentDataContainer());
  }

  public static Optional<String> displayName(StorageKeys keys, PersistentDataContainer pdc) {
    if (keys == null || pdc == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        StorageNameNormalizer.normalize(pdc.get(keys.storageName(), PersistentDataType.STRING)));
  }

  public static boolean apply(StorageKeys keys, ItemStack stack, String rawName) {
    if (!isStorageItem(keys, stack)) {
      return false;
    }
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    apply(keys, meta, pdc, rawName);
    stack.setItemMeta(meta);
    return true;
  }

  public static void apply(
      StorageKeys keys, ItemMeta meta, PersistentDataContainer pdc, String rawName) {
    apply(keys, meta, pdc, rawName, StorageDisplayName.customNameComponent(rawName));
  }

  public static void apply(
      StorageKeys keys,
      ItemMeta meta,
      PersistentDataContainer pdc,
      String rawName,
      Component customName) {
    String normalized = StorageNameNormalizer.normalize(rawName);
    if (normalized == null) {
      pdc.remove(keys.storageName());
      meta.customName(null);
      return;
    }
    pdc.set(keys.storageName(), PersistentDataType.STRING, normalized);
    meta.customName(
        customName == null ? StorageDisplayName.customNameComponent(normalized) : customName);
  }
}
