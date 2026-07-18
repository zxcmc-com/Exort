package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
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
}
