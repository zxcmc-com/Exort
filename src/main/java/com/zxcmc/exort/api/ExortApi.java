package com.zxcmc.exort.api;

import com.zxcmc.exort.api.model.ExortBlockDescriptor;
import com.zxcmc.exort.api.model.ExortItemDescriptor;
import com.zxcmc.exort.api.model.StorageTierDescriptor;
import java.util.Collection;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Experimental read-only API exposed through Bukkit's {@code ServicesManager}.
 *
 * <p>Inspection confirms only that content has a valid Exort shape. It does not authorize access,
 * ownership, protection bypass, or copying.
 */
public interface ExortApi {
  /**
   * Returns the experimental API contract version.
   *
   * <p>Breaking contract changes increment this value while Exort remains below 1.0.
   *
   * @return the current API contract version
   */
  int getApiVersion();

  /**
   * Returns the running plugin version string.
   *
   * @return the Exort plugin version
   */
  String getVersion();

  /**
   * Returns an immutable storage tier descriptor for the given key.
   *
   * <p>The returned descriptor is a public projection and does not expose Exort's internal mutable
   * registry or implementation classes.
   *
   * @param key configured tier key
   * @return the current descriptor, or an empty result when the key is unknown
   */
  Optional<StorageTierDescriptor> getStorageTier(String key);

  /**
   * Returns immutable descriptors for all currently configured storage tiers.
   *
   * @return an immutable snapshot of the current tier descriptors
   */
  Collection<StorageTierDescriptor> getStorageTiers();

  /**
   * Inspects a loaded block without loading any chunks.
   *
   * <p>This method reads Bukkit world and marker state and must be called on the primary server
   * thread. The returned descriptor is immutable and may be retained or read asynchronously.
   *
   * @param block loaded block to inspect
   * @return a descriptor for valid Exort content, otherwise an empty result
   * @throws IllegalStateException when called asynchronously while the server is running
   */
  Optional<ExortBlockDescriptor> inspectBlock(Block block);

  /**
   * Inspects an item without mutating it.
   *
   * <p>This method reads Bukkit item metadata and must be called on the primary server thread. The
   * returned descriptor is immutable and may be retained or read asynchronously.
   *
   * @param item item to inspect
   * @return a descriptor for valid Exort content, otherwise an empty result
   * @throws IllegalStateException when called asynchronously while the server is running
   */
  Optional<ExortItemDescriptor> inspectItem(ItemStack item);

  /**
   * Returns whether the loaded block is an Exort-managed block in the current runtime mode.
   *
   * <p>This is a read-only check for external integrations. It does not load chunks and returns
   * {@code false} for {@code null}, stale carriers without matching Exort marker data, and
   * client-only visual proxies.
   *
   * <p>This method reads Bukkit world and marker state and must be called on the primary server
   * thread.
   *
   * @param block loaded block to inspect
   * @return whether the block is managed by Exort
   * @throws IllegalStateException when called asynchronously while the server is running
   */
  boolean isExortBlock(Block block);

  /**
   * Returns whether the loaded block is an Exort-managed block backed by a real {@code
   * CHORUS_PLANT} carrier.
   *
   * <p>This is intended for chorus-carrier compatibility checks. It returns {@code false} for
   * BARRIER fallback carriers, VANILLA-mode carriers, stale markers, and client-only visual
   * proxies.
   *
   * <p>This method reads Bukkit world and marker state and must be called on the primary server
   * thread.
   *
   * @param block loaded block to inspect
   * @return whether the block is a managed Exort chorus carrier
   * @throws IllegalStateException when called asynchronously while the server is running
   */
  boolean isExortChorusCarrier(Block block);
}
