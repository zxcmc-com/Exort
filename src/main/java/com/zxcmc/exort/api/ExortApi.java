package com.zxcmc.exort.api;

import com.zxcmc.exort.api.model.StorageTierDescriptor;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.block.Block;

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

  /**
   * Returns whether the loaded block is an Exort-managed block in the current runtime mode.
   *
   * <p>This is a read-only check for external integrations. It does not load chunks and returns
   * {@code false} for {@code null}, stale carriers without matching Exort marker data, and
   * client-only visual proxies.
   */
  boolean isExortBlock(Block block);

  /**
   * Returns whether the loaded block is an Exort-managed block backed by a real {@code
   * CHORUS_PLANT} carrier.
   *
   * <p>This is intended for chorus-carrier compatibility checks. It returns {@code false} for
   * BARRIER fallback carriers, VANILLA-mode carriers, stale markers, and client-only visual
   * proxies.
   */
  boolean isExortChorusCarrier(Block block);
}
