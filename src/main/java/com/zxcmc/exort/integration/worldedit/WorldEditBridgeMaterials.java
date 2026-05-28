package com.zxcmc.exort.integration.worldedit;

import java.util.Objects;
import org.bukkit.Material;

public record WorldEditBridgeMaterials(
    Material wire,
    Material storageCarrier,
    Material terminalCarrier,
    Material monitorCarrier,
    Material busCarrier) {
  public WorldEditBridgeMaterials {
    Objects.requireNonNull(wire, "wire");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(terminalCarrier, "terminalCarrier");
    Objects.requireNonNull(monitorCarrier, "monitorCarrier");
    Objects.requireNonNull(busCarrier, "busCarrier");
  }
}
