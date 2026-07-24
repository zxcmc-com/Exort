package com.zxcmc.exort.api.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stable identifiers for Exort content exposed by the read-only API.
 *
 * <p>Enum constant names are source-level conveniences. Integrations that persist or exchange a
 * content type should use {@link #id()}.
 */
public enum ExortContentType {
  /** A tiered Storage block or item. */
  STORAGE("storage"),
  /** A Storage Core. */
  STORAGE_CORE("storage_core"),
  /** A Storage Terminal. */
  TERMINAL("terminal"),
  /** A Crafting Terminal. */
  CRAFTING_TERMINAL("crafting_terminal"),
  /** A network wire. */
  WIRE("wire"),
  /** A Storage Monitor. */
  MONITOR("monitor"),
  /** An Import Bus. */
  IMPORT_BUS("import_bus"),
  /** An Export Bus. */
  EXPORT_BUS("export_bus"),
  /** A network relay. */
  RELAY("relay"),
  /** A Wireless Transmitter block. */
  TRANSMITTER("transmitter"),
  /** A global Chunk Loader. */
  CHUNK_LOADER("chunk_loader"),
  /** A player-owned Personal Chunk Loader. */
  PERSONAL_CHUNK_LOADER("personal_chunk_loader"),
  /** A Dormant Chunk Loader. */
  DORMANT_CHUNK_LOADER("dormant_chunk_loader"),
  /** A stateful Wireless Terminal item. */
  WIRELESS_TERMINAL("wireless_terminal"),
  /** A tiered Wireless Booster item. */
  WIRELESS_BOOSTER("wireless_booster");

  private static final Map<String, ExortContentType> BY_ID =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(ExortContentType::id, Function.identity()));

  private final String id;

  ExortContentType(String id) {
    this.id = id;
  }

  /**
   * Returns the stable, lowercase API identifier.
   *
   * @return the stable identifier
   */
  public String id() {
    return id;
  }

  /**
   * Resolves a stable identifier after trimming and lowercase normalization.
   *
   * @param rawId identifier supplied by an integration
   * @return the matching content type, or an empty result for null, blank, or unknown input
   */
  public static Optional<ExortContentType> fromId(String rawId) {
    if (rawId == null || rawId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_ID.get(rawId.trim().toLowerCase(Locale.ROOT)));
  }
}
