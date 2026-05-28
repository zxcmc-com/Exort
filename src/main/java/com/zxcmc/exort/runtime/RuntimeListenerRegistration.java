package com.zxcmc.exort.runtime;

import com.zxcmc.exort.placement.RightClickPlacementGuard;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.recipes.RecipeService;

public record RuntimeListenerRegistration(
    RightClickPlacementGuard placementGuard,
    CraftingRules craftingRules,
    RecipeService recipeService) {}
