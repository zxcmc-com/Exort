package com.zxcmc.exort.api;

import com.zxcmc.exort.api.model.StorageTierDescriptor;
import java.util.Collection;
import java.util.Optional;

/** Minimal public API exposed by the Exort plugin instance. */
public interface ExortApi {
  /** Returns the running plugin version string. */
  String getVersion();

  /**
   * Returns an immutable storage tier descriptor for the given key.
   *
   * <p>The returned descriptor is a public projection and does not expose Exort's internal mutable
   * registry or implementation classes.
   */
  Optional<StorageTierDescriptor> getStorageTier(String key);

  /** Returns immutable descriptors for all currently configured storage tiers. */
  Collection<StorageTierDescriptor> getStorageTiers();
}
