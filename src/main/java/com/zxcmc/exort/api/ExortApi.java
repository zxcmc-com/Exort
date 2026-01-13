package com.zxcmc.exort.api;

import com.zxcmc.exort.storage.StorageTier;
import java.util.Collection;
import java.util.Optional;

public interface ExortApi {
  String getVersion();

  Optional<StorageTier> getStorageTier(String key);

  Collection<StorageTier> getStorageTiers();
}
