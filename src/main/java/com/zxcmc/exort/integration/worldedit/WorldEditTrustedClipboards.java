package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Identity-based trust stamps for clipboards produced by an authoritative local copy. */
final class WorldEditTrustedClipboards {
  private static final int MAX_ACTORS = 256;

  private final Map<UUID, Stamp> byActor = new LinkedHashMap<>();

  synchronized void trust(UUID actorId, Clipboard clipboard, PendingClipboardPatch patch) {
    if (actorId == null || clipboard == null || patch == null || !patch.matches(clipboard)) return;
    byActor.remove(actorId);
    while (byActor.size() >= MAX_ACTORS) {
      Iterator<UUID> iterator = byActor.keySet().iterator();
      if (!iterator.hasNext()) break;
      iterator.next();
      iterator.remove();
    }
    byActor.put(actorId, new Stamp(clipboard, patch.expectedBounds(), patch.expectedOrigin()));
  }

  synchronized boolean matches(UUID actorId, Clipboard clipboard) {
    Stamp stamp = actorId == null ? null : byActor.get(actorId);
    if (stamp == null || stamp.clipboard() != clipboard || clipboard == null) return false;
    Region region = clipboard.getRegion();
    return stamp.bounds().equals(WorldEditBounds.from(region))
        && stamp.origin().equals(clipboard.getOrigin());
  }

  synchronized void clear(UUID actorId) {
    if (actorId != null) byActor.remove(actorId);
  }

  synchronized void clear() {
    byActor.clear();
  }

  synchronized int size() {
    return byActor.size();
  }

  private record Stamp(Clipboard clipboard, WorldEditBounds bounds, BlockVector3 origin) {}
}
