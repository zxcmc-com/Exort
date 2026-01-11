package com.zxcmc.exort.wireless;

import com.zxcmc.exort.storage.StorageTier;

public record WirelessMeta(StorageTier tier, String storageId, String owner, String ownerName) {
}
