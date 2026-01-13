package com.zxcmc.exort.core.keys;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public class StorageKeys {
  private final NamespacedKey storageId;
  private final NamespacedKey type;
  private final NamespacedKey storageTier;
  private final NamespacedKey nestedCount;
  // Wireless terminal keys
  private final NamespacedKey wirelessOwner;
  private final NamespacedKey wirelessOwnerName;
  private final NamespacedKey wirelessCharge;
  private final NamespacedKey wirelessStorageId;
  private final NamespacedKey wirelessStoredAt;
  private final NamespacedKey wirelessStorageWorld;
  private final NamespacedKey wirelessStorageX;
  private final NamespacedKey wirelessStorageY;
  private final NamespacedKey wirelessStorageZ;
  private final NamespacedKey wirelessTier;

  public StorageKeys(Plugin plugin) {
    this.storageId = new NamespacedKey(plugin, "storage_id");
    this.type = new NamespacedKey(plugin, "type");
    this.storageTier = new NamespacedKey(plugin, "storage_tier");
    this.nestedCount = new NamespacedKey(plugin, "nested_items");
    this.wirelessOwner = new NamespacedKey(plugin, "wireless_owner");
    this.wirelessOwnerName = new NamespacedKey(plugin, "wireless_owner_name");
    this.wirelessCharge = new NamespacedKey(plugin, "wireless_charge");
    this.wirelessStorageId = new NamespacedKey(plugin, "wireless_storage_id");
    this.wirelessStoredAt = new NamespacedKey(plugin, "wireless_stored_at");
    this.wirelessStorageWorld = new NamespacedKey(plugin, "wireless_storage_world");
    this.wirelessStorageX = new NamespacedKey(plugin, "wireless_storage_x");
    this.wirelessStorageY = new NamespacedKey(plugin, "wireless_storage_y");
    this.wirelessStorageZ = new NamespacedKey(plugin, "wireless_storage_z");
    this.wirelessTier = new NamespacedKey(plugin, "wireless_tier");
  }

  public NamespacedKey storageId() {
    return storageId;
  }

  public NamespacedKey type() {
    return type;
  }

  public NamespacedKey storageTier() {
    return storageTier;
  }

  public NamespacedKey nestedCount() {
    return nestedCount;
  }

  public NamespacedKey wirelessOwner() {
    return wirelessOwner;
  }

  public NamespacedKey wirelessOwnerName() {
    return wirelessOwnerName;
  }

  public NamespacedKey wirelessCharge() {
    return wirelessCharge;
  }

  public NamespacedKey wirelessStorageId() {
    return wirelessStorageId;
  }

  public NamespacedKey wirelessStoredAt() {
    return wirelessStoredAt;
  }

  public NamespacedKey wirelessStorageWorld() {
    return wirelessStorageWorld;
  }

  public NamespacedKey wirelessStorageX() {
    return wirelessStorageX;
  }

  public NamespacedKey wirelessStorageY() {
    return wirelessStorageY;
  }

  public NamespacedKey wirelessStorageZ() {
    return wirelessStorageZ;
  }

  public NamespacedKey wirelessTier() {
    return wirelessTier;
  }
}
