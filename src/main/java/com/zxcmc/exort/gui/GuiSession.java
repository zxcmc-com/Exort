package com.zxcmc.exort.gui;

import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

public interface GuiSession extends InventoryHolder {
  SessionType type();

  Player getViewer();

  StorageCache getCache();

  StorageTier getTier();

  String getStorageId();

  Block getTerminalBlock();

  Location getStorageLocation();

  boolean isReadOnly();

  void setReadOnly(boolean readOnly);

  void render();

  void onClose();

  default void onSortEvent(SortEvent event) {}
}
