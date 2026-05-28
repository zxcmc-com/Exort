package com.zxcmc.exort.runtime;

import java.util.Objects;
import org.bukkit.Material;

public record RuntimeMaterials(
    Material wire,
    Material storageCarrier,
    Material terminalCarrier,
    Material monitorCarrier,
    Material busCarrier) {
  public RuntimeMaterials {
    Objects.requireNonNull(wire, "wire");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(terminalCarrier, "terminalCarrier");
    Objects.requireNonNull(monitorCarrier, "monitorCarrier");
    Objects.requireNonNull(busCarrier, "busCarrier");
  }
}
