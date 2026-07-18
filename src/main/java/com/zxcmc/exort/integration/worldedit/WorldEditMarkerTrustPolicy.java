package com.zxcmc.exort.integration.worldedit;

import org.enginehub.linbus.tree.LinCompoundTag;

/** Fail-closed rules for marker-bearing writes entering the WorldEdit extent. */
final class WorldEditMarkerTrustPolicy {
  private WorldEditMarkerTrustPolicy() {}

  static boolean rejectIncoming(
      LinCompoundTag exortTag, MarkerSnapshot parsed, boolean trustedMarkerSource) {
    return exortTag != null && (parsed == null || !trustedMarkerSource);
  }

  static boolean rejectExisting(MarkerSnapshot existing, boolean commandContextPresent) {
    return existing != null && !commandContextPresent;
  }
}
