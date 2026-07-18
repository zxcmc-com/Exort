package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Map;
import java.util.UUID;
import org.enginehub.linbus.tree.LinCompoundTag;

record CapturedMarkers(
    UUID sourceWorldId,
    WorldEditBounds bounds,
    BlockVector3 origin,
    Map<BlockVector3, LinCompoundTag> markers,
    int capturedChunks) {
  CapturedMarkers {
    markers = markers == null ? Map.of() : Map.copyOf(markers);
    capturedChunks = Math.max(0, capturedChunks);
  }

  static CapturedMarkers empty(UUID worldId) {
    return new CapturedMarkers(worldId, null, null, Map.of(), 0);
  }
}
