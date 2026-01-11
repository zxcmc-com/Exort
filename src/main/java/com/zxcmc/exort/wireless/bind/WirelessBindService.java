package com.zxcmc.exort.wireless.bind;

import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessMeta;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

public final class WirelessBindService {
    private final StorageKeys keys;

    public WirelessBindService(StorageKeys keys) {
        this.keys = keys;
    }

    public void bind(PersistentDataContainer pdc, Player player, String storageId, StorageTier tier, Location storageLoc) {
        pdc.set(keys.wirelessOwner(), PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(keys.wirelessOwnerName(), PersistentDataType.STRING, player.getName());
        pdc.set(keys.wirelessStorageId(), PersistentDataType.STRING, storageId);
        pdc.set(keys.wirelessTier(), PersistentDataType.STRING, tier.key());
        pdc.set(keys.wirelessStorageWorld(), PersistentDataType.STRING, storageLoc.getWorld().getUID().toString());
        pdc.set(keys.wirelessStorageX(), PersistentDataType.INTEGER, storageLoc.getBlockX());
        pdc.set(keys.wirelessStorageY(), PersistentDataType.INTEGER, storageLoc.getBlockY());
        pdc.set(keys.wirelessStorageZ(), PersistentDataType.INTEGER, storageLoc.getBlockZ());
    }

    public void unbind(PersistentDataContainer pdc) {
        pdc.remove(keys.wirelessOwner());
        pdc.remove(keys.wirelessOwnerName());
        pdc.remove(keys.wirelessStorageId());
        pdc.remove(keys.wirelessStorageWorld());
        pdc.remove(keys.wirelessStorageX());
        pdc.remove(keys.wirelessStorageY());
        pdc.remove(keys.wirelessStorageZ());
    }

    public WirelessMeta readMeta(PersistentDataContainer pdc) {
        StorageTier tier = null;
        String tierRaw = pdc.get(keys.wirelessTier(), PersistentDataType.STRING);
        if (tierRaw != null) {
            tier = StorageTier.fromString(tierRaw).orElse(null);
        }
        String storageId = pdc.get(keys.wirelessStorageId(), PersistentDataType.STRING);
        String owner = pdc.get(keys.wirelessOwner(), PersistentDataType.STRING);
        String ownerName = pdc.get(keys.wirelessOwnerName(), PersistentDataType.STRING);
        return new WirelessMeta(tier, storageId, owner, ownerName);
    }

    public boolean isLinked(PersistentDataContainer pdc) {
        return pdc.has(keys.wirelessStorageId(), PersistentDataType.STRING);
    }

    public String storageId(PersistentDataContainer pdc) {
        return pdc.get(keys.wirelessStorageId(), PersistentDataType.STRING);
    }

    public String owner(PersistentDataContainer pdc) {
        return pdc.get(keys.wirelessOwner(), PersistentDataType.STRING);
    }

    public Optional<StorageTier> tier(PersistentDataContainer pdc) {
        String raw = pdc.get(keys.wirelessTier(), PersistentDataType.STRING);
        return StorageTier.fromString(raw);
    }

    public Location storageLocation(PersistentDataContainer pdc) {
        String worldId = pdc.get(keys.wirelessStorageWorld(), PersistentDataType.STRING);
        Integer x = pdc.get(keys.wirelessStorageX(), PersistentDataType.INTEGER);
        Integer y = pdc.get(keys.wirelessStorageY(), PersistentDataType.INTEGER);
        Integer z = pdc.get(keys.wirelessStorageZ(), PersistentDataType.INTEGER);
        if (worldId == null || x == null || y == null || z == null) return null;
        try {
            var world = Bukkit.getWorld(UUID.fromString(worldId));
            if (world == null) return null;
            return new Location(world, x, y, z);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
