package com.zxcmc.exort.integration.chorusfix.embedded;

import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public record EmbeddedChorusfixConfig(
    boolean enabled,
    boolean onlyWhenPaperDisabled,
    int maxPerEvent,
    int maxPerTick,
    int maxChainDepth,
    Set<ChorusFaceMask> ignoredMasks,
    boolean debug) {
  private static final int DEFAULT_MAX_PER_EVENT = 256;
  private static final int DEFAULT_MAX_PER_TICK = 512;
  private static final int DEFAULT_MAX_CHAIN_DEPTH = 4096;

  public static EmbeddedChorusfixConfig from(ConfigurationSection config) {
    boolean enabled = config == null || config.getBoolean("chorusfix", true);
    return new EmbeddedChorusfixConfig(
        enabled,
        true,
        DEFAULT_MAX_PER_EVENT,
        DEFAULT_MAX_PER_TICK,
        DEFAULT_MAX_CHAIN_DEPTH,
        Set.of(ChorusFaceMask.ALL),
        false);
  }
}
