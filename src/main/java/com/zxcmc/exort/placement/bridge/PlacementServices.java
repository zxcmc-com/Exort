package com.zxcmc.exort.placement.bridge;

import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.placement.storage.StoragePlacementService;
import com.zxcmc.exort.storage.StorageClaimRegistry;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** Domain services required by both placement transports. */
public record PlacementServices(
    JavaPlugin plugin,
    StorageManager storageManager,
    StorageClaimRegistry storageClaimRegistry,
    CustomItems customItems,
    StorageKeys keys,
    WirelessTransmitterService wirelessTransmitterService,
    RegionProtection regionProtection,
    PlayerFeedback playerFeedback,
    ChunkLoaderService chunkLoaderService,
    StoragePlacementService storagePlacementService) {
  public PlacementServices {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(storageClaimRegistry, "storageClaimRegistry");
    Objects.requireNonNull(customItems, "customItems");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(wirelessTransmitterService, "wirelessTransmitterService");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(chunkLoaderService, "chunkLoaderService");
    Objects.requireNonNull(storagePlacementService, "storagePlacementService");
  }
}
