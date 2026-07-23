package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.enginehub.linbus.tree.LinCompoundTag;

record PendingClipboardPatch(
    UUID sourceWorldId,
    WorldEditBounds expectedBounds,
    BlockVector3 expectedOrigin,
    Map<BlockVector3, LinCompoundTag> markers) {
  PendingClipboardPatch {
    markers = markers == null ? Map.of() : Map.copyOf(markers);
  }

  boolean matches(Clipboard clipboard) {
    return clipboard != null
        && expectedBounds != null
        && expectedOrigin != null
        && expectedBounds.equals(WorldEditBounds.from(clipboard.getRegion()))
        && expectedOrigin.equals(clipboard.getOrigin());
  }

  PendingClipboardPatch rebaseTo(Clipboard clipboard) {
    if (clipboard == null || expectedBounds == null || expectedOrigin == null) return null;
    WorldEditBounds actualBounds = WorldEditBounds.from(clipboard.getRegion());
    BlockVector3 actualOrigin = clipboard.getOrigin();
    if (actualBounds == null
        || actualOrigin == null
        || expectedBounds.sizeX() != actualBounds.sizeX()
        || expectedBounds.sizeY() != actualBounds.sizeY()
        || expectedBounds.sizeZ() != actualBounds.sizeZ()) {
      return null;
    }
    BlockVector3 offset = actualBounds.minimum().subtract(expectedBounds.minimum());
    Map<BlockVector3, LinCompoundTag> rebasedMarkers = new HashMap<>();
    for (Map.Entry<BlockVector3, LinCompoundTag> entry : markers.entrySet()) {
      BlockVector3 rebased = entry.getKey().add(offset);
      if (!actualBounds.contains(rebased)) return null;
      rebasedMarkers.put(rebased, entry.getValue());
    }
    return new PendingClipboardPatch(sourceWorldId, actualBounds, actualOrigin, rebasedMarkers);
  }
}
