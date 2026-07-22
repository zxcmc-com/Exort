package com.zxcmc.exort.runtime;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Internal construction checkpoints used only by the isolated compatibility harness. */
public enum RuntimeConstructionStage {
  GENERATION_CORE_PREPARATION,
  ITEM_WIRELESS,
  TRANSMITTER,
  PACKET_EVENTS,
  CHUNK_LOADER,
  CLAIMS_RELAY_PROTECTION,
  DISPLAY,
  BUS,
  BREAKING,
  LISTENERS_RECIPES,
  POST_REFRESH,
  WORLD_EDIT,
  MAINTENANCE,
  SERVICES_COMPLETE,
  PUBLICATION_FIELDS,
  FINAL_PUBLISH;

  static Optional<RuntimeConstructionStage> parse(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String normalized = value.strip().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(stage -> stage.name().equals(normalized)).findFirst();
  }
}
