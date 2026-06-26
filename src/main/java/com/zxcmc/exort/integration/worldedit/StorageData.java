package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.storage.StorageDisplayName;

record StorageData(
    String storageId, String tier, Long tierMaxItems, String facing, String displayName)
    implements FacingOwner {
  StorageData {
    displayName = StorageDisplayName.normalize(displayName);
  }

  StorageData(String storageId, String tier, String facing) {
    this(storageId, tier, null, facing, null);
  }

  StorageData(String storageId, String tier, Long tierMaxItems, String facing) {
    this(storageId, tier, tierMaxItems, facing, null);
  }
}
