package com.zxcmc.exort.runtime;

import com.zxcmc.exort.platform.RuntimeModeState;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;

/** Exact last-known-good state required to reconstruct a closed runtime generation. */
public record RuntimeCheckpoint(
    FileConfiguration config, RuntimeModeState mode, PreparedRuntime preparedRuntime) {
  public RuntimeCheckpoint {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(preparedRuntime, "preparedRuntime");
  }
}
