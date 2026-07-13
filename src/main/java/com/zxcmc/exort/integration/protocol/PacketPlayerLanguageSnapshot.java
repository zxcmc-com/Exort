package com.zxcmc.exort.integration.protocol;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Copy-on-write player-language state for packet workers.
 *
 * <p>All updates are expected from the server thread. Packet threads only read the immutable state
 * published through {@link AtomicReference}; they never access Bukkit player state.
 */
final class PacketPlayerLanguageSnapshot {
  private final AtomicLong generations = new AtomicLong();
  private final AtomicReference<State> state = new AtomicReference<>(new State(0L, Map.of()));

  void replace(Map<UUID, String> languages) {
    Map<UUID, String> copy = new LinkedHashMap<>();
    if (languages != null) {
      languages.forEach(
          (playerId, language) -> {
            if (playerId != null && language != null && !language.isBlank()) {
              copy.put(playerId, language);
            }
          });
    }
    publish(Map.copyOf(copy));
  }

  void update(UUID playerId, String language) {
    if (playerId == null || language == null || language.isBlank()) {
      return;
    }
    while (true) {
      State current = state.get();
      Map<UUID, String> updated = new LinkedHashMap<>(current.languages());
      updated.put(playerId, language);
      State replacement = new State(generations.incrementAndGet(), Map.copyOf(updated));
      if (state.compareAndSet(current, replacement)) {
        return;
      }
    }
  }

  void remove(UUID playerId) {
    if (playerId == null) {
      return;
    }
    while (true) {
      State current = state.get();
      if (!current.languages().containsKey(playerId)) {
        return;
      }
      Map<UUID, String> updated = new LinkedHashMap<>(current.languages());
      updated.remove(playerId);
      State replacement = new State(generations.incrementAndGet(), Map.copyOf(updated));
      if (state.compareAndSet(current, replacement)) {
        return;
      }
    }
  }

  String language(UUID playerId, String fallback) {
    if (playerId == null) {
      return fallback;
    }
    return state.get().languages().getOrDefault(playerId, fallback);
  }

  State current() {
    return state.get();
  }

  private void publish(Map<UUID, String> languages) {
    state.set(new State(generations.incrementAndGet(), languages));
  }

  record State(long generation, Map<UUID, String> languages) {}
}
