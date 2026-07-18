package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.extension.platform.Actor;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded command-context hand-off from Bukkit command events to WorldEdit edit sessions. */
final class WorldEditOperationContexts {
  static final int MAX_ACTORS = 256;

  private final long ttlMs;
  private final AtomicLong generations = new AtomicLong();
  private final Map<UUID, Entry> byActor = new LinkedHashMap<>();

  WorldEditOperationContexts(long ttlMs) {
    this.ttlMs = Math.max(1L, ttlMs);
  }

  synchronized WorldEditOperationContext remember(
      Actor actor, WorldEditOperationContext context, long now) {
    if (actor == null || actor.getUniqueId() == null || context == null) return null;
    prune(now);
    UUID actorId = actor.getUniqueId();
    byActor.remove(actorId);
    while (byActor.size() >= MAX_ACTORS) {
      Iterator<UUID> iterator = byActor.keySet().iterator();
      if (!iterator.hasNext()) break;
      iterator.next();
      iterator.remove();
    }
    WorldEditOperationContext prepared = context.withGeneration(generations.incrementAndGet());
    byActor.put(actorId, new Entry(actor, prepared));
    return prepared;
  }

  synchronized Resolution resolve(UUID actorId, UUID worldId, long now) {
    prune(now);
    if (actorId != null) {
      Entry entry = byActor.get(actorId);
      return entry != null && entry.context().appliesTo(worldId)
          ? new Resolution(entry.actor(), entry.context(), false)
          : null;
    }
    Entry match = null;
    for (Entry entry : byActor.values()) {
      if (!entry.context().appliesTo(worldId)) continue;
      if (match != null && !Objects.equals(match.context().actorId(), entry.context().actorId())) {
        return new Resolution(null, null, true);
      }
      match = entry;
    }
    return match == null ? null : new Resolution(match.actor(), match.context(), false);
  }

  synchronized void clear(UUID actorId) {
    if (actorId != null) byActor.remove(actorId);
  }

  synchronized void clear() {
    byActor.clear();
  }

  synchronized int size(long now) {
    prune(now);
    return byActor.size();
  }

  private void prune(long now) {
    byActor.values().removeIf(entry -> now - entry.context().timestampMs() > ttlMs);
  }

  record Resolution(Actor actor, WorldEditOperationContext context, boolean ambiguous) {}

  private record Entry(Actor actor, WorldEditOperationContext context) {}
}
