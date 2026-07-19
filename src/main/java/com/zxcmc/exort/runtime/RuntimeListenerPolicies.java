package com.zxcmc.exort.runtime;

import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.recipes.CraftingRulesConfig;
import java.util.Objects;
import org.bukkit.Material;

public record RuntimeListenerPolicies(
    BusRuntimeConfig busRuntimeConfig,
    CraftingRulesConfig craftingConfig,
    Material relayTraversalCarrier) {
  public RuntimeListenerPolicies {
    Objects.requireNonNull(busRuntimeConfig, "busRuntimeConfig");
    Objects.requireNonNull(craftingConfig, "craftingConfig");
    Objects.requireNonNull(relayTraversalCarrier, "relayTraversalCarrier");
  }
}
