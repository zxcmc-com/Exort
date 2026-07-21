package com.zxcmc.exort.runtime;

import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.recipes.RecipeService;
import java.util.Objects;
import java.util.function.Supplier;

public record RuntimeListenerIntegrations(
    RegionProtection regionProtection,
    AuthenticationGate authenticationGate,
    WorldEditWandGuard worldEditWandGuard,
    PacketEnhancements packetEnhancements,
    Supplier<NetworkGraphCache> networkGraphCache,
    RecipeService.Activation recipeActivation) {
  public RuntimeListenerIntegrations {
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(authenticationGate, "authenticationGate");
    Objects.requireNonNull(worldEditWandGuard, "worldEditWandGuard");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(recipeActivation, "recipeActivation");
  }
}
