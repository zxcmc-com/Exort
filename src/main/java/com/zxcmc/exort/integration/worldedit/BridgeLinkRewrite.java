package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Map;
import java.util.UUID;

final class BridgeLinkRewrite {
  private BridgeLinkRewrite() {}

  static MarkerSnapshot rewrite(
      MarkerSnapshot snapshot,
      BlockVector3 sourcePosition,
      UUID sourceWorldId,
      UUID destinationWorldId,
      Map<BlockVector3, BlockVector3> destinations,
      Map<BlockVector3, MarkerSnapshot> snapshots) {
    if (snapshot == null || snapshot.bridge() == null) {
      return snapshot;
    }
    BridgeLinkData link = snapshot.bridge().link();
    if (link == null) {
      return snapshot;
    }
    if (sourceWorldId == null
        || destinationWorldId == null
        || !sourceWorldId.equals(link.worldId())) {
      return withBridge(snapshot, new BridgeData(null));
    }
    BlockVector3 peerSource = BlockVector3.at(link.x(), link.y(), link.z());
    BlockVector3 peerDestination = destinations.get(peerSource);
    MarkerSnapshot peerSnapshot = snapshots.get(peerSource);
    if (!isReciprocalBridgeSnapshot(peerSnapshot, sourceWorldId, sourcePosition)) {
      return withBridge(snapshot, new BridgeData(null));
    }
    if (peerDestination == null) {
      return withBridge(snapshot, new BridgeData(null));
    }
    return withBridge(
        snapshot,
        new BridgeData(
            new BridgeLinkData(
                destinationWorldId,
                peerDestination.x(),
                peerDestination.y(),
                peerDestination.z())));
  }

  private static boolean isReciprocalBridgeSnapshot(
      MarkerSnapshot snapshot, UUID sourceWorldId, BlockVector3 expectedPeer) {
    if (snapshot == null || snapshot.bridge() == null || expectedPeer == null) {
      return false;
    }
    BridgeLinkData link = snapshot.bridge().link();
    return link != null
        && link.worldId().equals(sourceWorldId)
        && link.x() == expectedPeer.x()
        && link.y() == expectedPeer.y()
        && link.z() == expectedPeer.z();
  }

  private static MarkerSnapshot withBridge(MarkerSnapshot snapshot, BridgeData bridge) {
    if (snapshot == null) return null;
    return new MarkerSnapshot(
        snapshot.storage(),
        snapshot.terminal(),
        snapshot.bus(),
        snapshot.monitor(),
        bridge,
        snapshot.wire(),
        snapshot.storageCore());
  }
}
