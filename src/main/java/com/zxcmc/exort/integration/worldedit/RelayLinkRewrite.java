package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Map;
import java.util.UUID;

final class RelayLinkRewrite {
  private RelayLinkRewrite() {}

  static MarkerSnapshot rewrite(
      MarkerSnapshot snapshot,
      BlockVector3 sourcePosition,
      UUID sourceWorldId,
      UUID destinationWorldId,
      Map<BlockVector3, BlockVector3> destinations,
      Map<BlockVector3, MarkerSnapshot> snapshots) {
    if (snapshot == null || snapshot.relay() == null) {
      return snapshot;
    }
    RelayLinkData link = snapshot.relay().link();
    if (link == null) {
      return snapshot;
    }
    if (sourceWorldId == null
        || destinationWorldId == null
        || !sourceWorldId.equals(link.worldId())) {
      return withRelay(snapshot, new RelayData(null));
    }
    BlockVector3 peerSource = BlockVector3.at(link.x(), link.y(), link.z());
    BlockVector3 peerDestination = destinations.get(peerSource);
    MarkerSnapshot peerSnapshot = snapshots.get(peerSource);
    if (!isReciprocalRelaySnapshot(peerSnapshot, sourceWorldId, sourcePosition)) {
      return withRelay(snapshot, new RelayData(null));
    }
    if (peerDestination == null) {
      return withRelay(snapshot, new RelayData(null));
    }
    return withRelay(
        snapshot,
        new RelayData(
            new RelayLinkData(
                destinationWorldId,
                peerDestination.x(),
                peerDestination.y(),
                peerDestination.z())));
  }

  private static boolean isReciprocalRelaySnapshot(
      MarkerSnapshot snapshot, UUID sourceWorldId, BlockVector3 expectedPeer) {
    if (snapshot == null || snapshot.relay() == null || expectedPeer == null) {
      return false;
    }
    RelayLinkData link = snapshot.relay().link();
    return link != null
        && link.worldId().equals(sourceWorldId)
        && link.x() == expectedPeer.x()
        && link.y() == expectedPeer.y()
        && link.z() == expectedPeer.z();
  }

  private static MarkerSnapshot withRelay(MarkerSnapshot snapshot, RelayData relay) {
    if (snapshot == null) return null;
    return new MarkerSnapshot(
        snapshot.storage(),
        snapshot.terminal(),
        snapshot.bus(),
        snapshot.monitor(),
        relay,
        snapshot.wire(),
        snapshot.storageCore());
  }
}
