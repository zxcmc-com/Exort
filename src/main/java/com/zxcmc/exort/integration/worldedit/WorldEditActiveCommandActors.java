package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.extension.platform.Actor;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded actor hand-off for FAWE edit-session events that omit their command actor. */
final class WorldEditActiveCommandActors {
  private static final int MAX_THREADS = 256;

  private final long ttlMs;
  private final Map<Long, Entry> byThread = new LinkedHashMap<>();
  private final Map<UUID, Entry> byActor = new LinkedHashMap<>();

  WorldEditActiveCommandActors(long ttlMs) {
    this.ttlMs = Math.max(1L, ttlMs);
  }

  synchronized void remember(long threadId, Actor actor, String worldName, long now) {
    prune(now);
    clear(threadId);
    if (actor == null || actor.getUniqueId() == null) return;
    clear(actor.getUniqueId());
    evictOldestIfFull();
    Entry entry = new Entry(actor, worldName, now);
    byThread.put(threadId, entry);
    byActor.remove(actor.getUniqueId());
    byActor.put(actor.getUniqueId(), entry);
  }

  synchronized Actor resolve(long threadId, long now) {
    prune(now);
    Entry entry = byThread.get(threadId);
    return entry == null ? null : entry.actor();
  }

  synchronized Actor resolve(String worldName, long now) {
    prune(now);
    Actor match = null;
    for (Entry entry : byActor.values()) {
      if (!Objects.equals(worldName, entry.worldName())) continue;
      if (match != null && !match.getUniqueId().equals(entry.actor().getUniqueId())) {
        return null;
      }
      match = entry.actor();
    }
    return match;
  }

  synchronized void clear(long threadId) {
    Entry removed = byThread.remove(threadId);
    if (removed != null) {
      byActor.remove(removed.actor().getUniqueId(), removed);
    }
  }

  synchronized void clear(UUID actorId) {
    if (actorId == null) return;
    Entry removed = byActor.remove(actorId);
    if (removed != null) {
      byThread.values().removeIf(entry -> entry == removed);
    }
  }

  synchronized void clear() {
    byThread.clear();
    byActor.clear();
  }

  synchronized int size() {
    return byActor.size();
  }

  private void prune(long now) {
    byThread.values().removeIf(entry -> now - entry.timestampMs() > ttlMs);
    byActor.values().removeIf(entry -> now - entry.timestampMs() > ttlMs);
  }

  private void evictOldestIfFull() {
    while (byActor.size() >= MAX_THREADS) {
      Iterator<Map.Entry<UUID, Entry>> iterator = byActor.entrySet().iterator();
      if (!iterator.hasNext()) break;
      Entry removed = iterator.next().getValue();
      iterator.remove();
      byThread.values().removeIf(entry -> entry == removed);
    }
  }

  private record Entry(Actor actor, String worldName, long timestampMs) {}
}
