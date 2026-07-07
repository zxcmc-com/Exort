package com.zxcmc.exort.wireless.access;

import com.zxcmc.exort.wireless.bind.WirelessBindService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public final class WirelessAccessService {
  private final WirelessBindService bindService;

  public WirelessAccessService(WirelessBindService bindService) {
    this.bindService = bindService;
  }

  public boolean isOwner(Player player, ItemStack stack) {
    if (player == null || stack == null || !stack.hasItemMeta()) return false;
    ItemMeta meta = stack.getItemMeta();
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    String owner = bindService.owner(pdc);
    return owner != null && owner.equalsIgnoreCase(player.getUniqueId().toString());
  }

  public boolean isLinked(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    return bindService.isLinked(pdc);
  }

  public Location storageLocation(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return null;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    return bindService.storageLocation(pdc);
  }
}
