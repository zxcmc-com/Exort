package com.zxcmc.exort.storage;

import java.util.Collection;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** Test-local catalog context for legacy fixtures that build tiers incrementally. */
public final class StorageTierTestFixtures {
  private static final ThreadLocal<StorageTierCatalog> CURRENT =
      ThreadLocal.withInitial(StorageTierCatalog::empty);

  private StorageTierTestFixtures() {}

  public static boolean load(ConfigurationSection section, Logger logger) {
    try {
      CURRENT.set(StorageTierCatalog.parse(section, logger));
      return true;
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }

  public static StorageTierCatalog current() {
    return CURRENT.get();
  }

  public static Optional<StorageTier> find(String key) {
    return current().find(key);
  }

  public static Collection<StorageTier> all() {
    return current().tiers();
  }

  public static void reset() {
    CURRENT.remove();
  }
}
