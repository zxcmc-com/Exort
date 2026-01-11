package com.zxcmc.exort.wireless.access;

import com.zxcmc.exort.wireless.bind.WirelessBindService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public final class WirelessAccessService {
    private final WirelessBindService bindService;
    private final int rangeChunks;

    public WirelessAccessService(WirelessBindService bindService, int rangeChunks) {
        this.bindService = bindService;
        this.rangeChunks = Math.max(0, rangeChunks);
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

    public boolean inRange(Location storage, Location playerLoc) {
        if (storage == null || playerLoc == null) return false;
        if (!storage.getWorld().equals(playerLoc.getWorld())) return false;
        int dx = Math.abs((storage.getBlockX() >> 4) - (playerLoc.getBlockX() >> 4));
        int dz = Math.abs((storage.getBlockZ() >> 4) - (playerLoc.getBlockZ() >> 4));
        return dx <= rangeChunks && dz <= rangeChunks;
    }
}
