package com.zxcmc.exort.runtime;

import com.zxcmc.exort.items.listener.InventoryRefreshListener;
import com.zxcmc.exort.placement.RightClickPlacementGuard;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.storage.StorageClaimReconciler;

public record RuntimeListenerRegistration(
    RightClickPlacementGuard placementGuard,
    CraftingRules craftingRules,
    RecipeService recipeService,
    StorageClaimReconciler storageClaimReconciler,
    InventoryRefreshListener inventoryRefreshListener) {}
