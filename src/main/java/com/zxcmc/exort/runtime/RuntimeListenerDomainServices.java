package com.zxcmc.exort.runtime;

import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.relay.RelaySetupTracker;
import com.zxcmc.exort.storage.StorageClaimRegistry;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.util.Objects;

public record RuntimeListenerDomainServices(
    StorageClaimRegistry storageClaimRegistry,
    CustomItems customItems,
    WirelessTerminalService wirelessService,
    WirelessTransmitterService wirelessTransmitterService,
    TransmitterSessionManager transmitterSessionManager,
    ChunkLoaderService chunkLoaderService,
    RelaySetupTracker relaySetupTracker) {
  public RuntimeListenerDomainServices {
    Objects.requireNonNull(storageClaimRegistry, "storageClaimRegistry");
    Objects.requireNonNull(customItems, "customItems");
    Objects.requireNonNull(wirelessService, "wirelessService");
    Objects.requireNonNull(wirelessTransmitterService, "wirelessTransmitterService");
    Objects.requireNonNull(transmitterSessionManager, "transmitterSessionManager");
    Objects.requireNonNull(chunkLoaderService, "chunkLoaderService");
    Objects.requireNonNull(relaySetupTracker, "relaySetupTracker");
  }
}
