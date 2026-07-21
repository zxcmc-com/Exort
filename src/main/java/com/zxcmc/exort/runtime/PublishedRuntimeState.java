package com.zxcmc.exort.runtime;

import com.zxcmc.exort.storage.StorageTierCatalog;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;

/** Single atomic publication unit for the active runtime generation. */
public record PublishedRuntimeState(
    ExortRuntimeServices services, FileConfiguration config, PreparedRuntime preparedRuntime) {
  public PublishedRuntimeState {
    Objects.requireNonNull(services, "services");
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(preparedRuntime, "preparedRuntime");
  }

  public StorageTierCatalog storageTierCatalog() {
    return preparedRuntime.storageTierCatalog();
  }
}
