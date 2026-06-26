package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Map;
import java.util.UUID;
import org.enginehub.linbus.tree.LinCompoundTag;

record CapturedMarkers(UUID sourceWorldId, Map<BlockVector3, LinCompoundTag> markers) {
  CapturedMarkers {
    markers = markers == null ? Map.of() : Map.copyOf(markers);
  }
}
