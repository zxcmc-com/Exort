package com.zxcmc.exort.infra.resourcepack.hosting;

import com.zxcmc.exort.infra.config.ConfigNumbers;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

record ResourcePackNumericConfig(int configurationTimeoutSeconds, int selfHostPort) {
  static final int MAX_CONFIGURATION_TIMEOUT_SECONDS = 300;
  static final int MAX_PORT = 65_535;

  static ResourcePackNumericConfig fromConfig(ConfigurationSection config, Logger logger) {
    ConfigNumbers numbers = new ConfigNumbers(config, logger);
    return new ResourcePackNumericConfig(
        numbers.integer(
            "resourcePack.configurationTimeoutSeconds", 30, 1, MAX_CONFIGURATION_TIMEOUT_SECONDS),
        numbers.integer("resourcePack.selfHost.port", 0, 0, MAX_PORT));
  }
}
