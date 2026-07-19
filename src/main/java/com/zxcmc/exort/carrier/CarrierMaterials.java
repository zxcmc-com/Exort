package com.zxcmc.exort.carrier;

import java.util.Objects;
import org.bukkit.Material;

public record CarrierMaterials(
    Material wire,
    Material storageCarrier,
    Material terminalCarrier,
    Material monitorCarrier,
    Material busCarrier,
    Material relayCarrier,
    Material transmitterCarrier,
    Material chunkLoaderCarrier) {
  public CarrierMaterials {
    Objects.requireNonNull(wire, "wire");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(terminalCarrier, "terminalCarrier");
    Objects.requireNonNull(monitorCarrier, "monitorCarrier");
    Objects.requireNonNull(busCarrier, "busCarrier");
    Objects.requireNonNull(relayCarrier, "relayCarrier");
    Objects.requireNonNull(transmitterCarrier, "transmitterCarrier");
    Objects.requireNonNull(chunkLoaderCarrier, "chunkLoaderCarrier");
  }
}
