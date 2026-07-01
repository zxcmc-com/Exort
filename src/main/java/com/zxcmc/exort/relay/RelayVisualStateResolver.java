package com.zxcmc.exort.relay;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.TerminalLinkFinder;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class RelayVisualStateResolver {
  private final Plugin plugin;
  private final StorageKeys keys;
  private final RelaySetupTracker setupTracker;
  private final int wireLimit;
  private final int wireHardCap;
  private final int relayRangeChunks;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material relayCarrier;

  public RelayVisualStateResolver(
      Plugin plugin,
      StorageKeys keys,
      RelaySetupTracker setupTracker,
      int wireLimit,
      int wireHardCap,
      int relayRangeChunks,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.keys = Objects.requireNonNull(keys, "keys");
    this.setupTracker = setupTracker;
    this.wireLimit = wireLimit;
    this.wireHardCap = wireHardCap;
    this.relayRangeChunks = relayRangeChunks;
    this.wireMaterial = Objects.requireNonNull(wireMaterial, "wireMaterial");
    this.storageCarrier = Objects.requireNonNull(storageCarrier, "storageCarrier");
    this.relayCarrier = Objects.requireNonNull(relayCarrier, "relayCarrier");
  }

  public RelayVisualState resolve(Block relay) {
    return resolve(relay, System.currentTimeMillis());
  }

  RelayVisualState resolve(Block relay, long nowMillis) {
    if (!isRelay(relay)) {
      return RelayVisualState.BLACK;
    }
    if (isPendingUnlinked(relay, nowMillis)) {
      return RelayVisualState.RED;
    }
    TerminalLinkFinder.StorageSearchResult storage = findStorage(relay);
    if (storage.count() > 1) {
      return RelayVisualState.RED;
    }
    boolean connectedToStorage = storage.count() == 1 && storage.data() != null;
    boolean linkedToPeer = hasWorkingPeer(relay);
    if (connectedToStorage && linkedToPeer) {
      return RelayVisualState.GREEN;
    }
    if (connectedToStorage) {
      return RelayVisualState.BLUE;
    }
    return linkedToPeer ? RelayVisualState.RED : RelayVisualState.BLACK;
  }

  private boolean isPendingUnlinked(Block relay, long nowMillis) {
    return setupTracker != null
        && setupTracker.isPending(relay, nowMillis)
        && RelayMarker.link(plugin, relay).isEmpty();
  }

  private TerminalLinkFinder.StorageSearchResult findStorage(Block relay) {
    return TerminalLinkFinder.find(
        relay,
        keys,
        plugin,
        wireLimit,
        wireHardCap,
        wireMaterial,
        storageCarrier,
        relayCarrier,
        relayRangeChunks);
  }

  private boolean hasWorkingPeer(Block relay) {
    if (relay == null || relay.getWorld() == null) {
      return false;
    }
    Block peer = RelayMarker.link(plugin, relay).map(RelayMarker.Link::loadedBlock).orElse(null);
    if (peer == null || peer.getWorld() == null) {
      return false;
    }
    if (!relay.getWorld().getUID().equals(peer.getWorld().getUID())) {
      return false;
    }
    if (!isRelay(peer)) {
      return false;
    }
    if (RelayMarker.link(plugin, peer).filter(back -> back.sameBlock(relay)).isEmpty()) {
      return false;
    }
    return NetworkGraphCache.inRelayRange(relay, peer, relayRangeChunks);
  }

  private boolean isRelay(Block block) {
    return Carriers.matchesCarrier(block, relayCarrier) && RelayMarker.isRelay(plugin, block);
  }
}
