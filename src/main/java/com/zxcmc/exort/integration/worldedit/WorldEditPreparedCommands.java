package com.zxcmc.exort.integration.worldedit;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded hand-off from synchronous Bukkit command events to WorldEdit command events. */
final class WorldEditPreparedCommands {
  private static final int MAX_ACTORS = 256;

  private final long ttlMs;
  private final Map<UUID, Stamp> byActor = new LinkedHashMap<>();

  WorldEditPreparedCommands(long ttlMs) {
    this.ttlMs = Math.max(1L, ttlMs);
  }

  synchronized void remember(UUID actorId, String command, long now) {
    if (actorId == null) return;
    prune(now);
    byActor.remove(actorId);
    while (byActor.size() >= MAX_ACTORS) {
      Iterator<UUID> iterator = byActor.keySet().iterator();
      if (!iterator.hasNext()) break;
      iterator.next();
      iterator.remove();
    }
    byActor.put(actorId, new Stamp(WorldEditCommandParser.commandSignature(command), now));
  }

  synchronized boolean consume(UUID actorId, String command, long now) {
    if (actorId == null) return false;
    prune(now);
    Stamp stamp = byActor.get(actorId);
    if (stamp == null
        || !stamp.signature().equals(WorldEditCommandParser.commandSignature(command))) {
      return false;
    }
    byActor.remove(actorId);
    return true;
  }

  synchronized void clear(UUID actorId) {
    if (actorId != null) {
      byActor.remove(actorId);
    }
  }

  synchronized void clear() {
    byActor.clear();
  }

  synchronized int size() {
    return byActor.size();
  }

  private void prune(long now) {
    byActor.values().removeIf(stamp -> now - stamp.timestampMs() > ttlMs);
  }

  private record Stamp(String signature, long timestampMs) {}
}
