package com.zxcmc.exort.api.model;

/**
 * Immutable public projection of an Exort storage tier.
 *
 * @param key stable tier key used in config, markers, items, and commands
 * @param maxItems maximum weighted item capacity for this tier
 * @param displayMaterialKey namespaced Bukkit material key used for tier display
 * @param displayName configured human-readable tier name
 */
public record StorageTierDescriptor(
    String key, long maxItems, String displayMaterialKey, String displayName) {}
