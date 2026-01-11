package com.zxcmc.exort.bus;

import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public record BusSettings(BusPos pos, BusType type, BusMode mode, ItemStack[] filters) {
    public BusSettings {
        if (filters == null) {
            filters = new ItemStack[0];
        }
        filters = Arrays.copyOf(filters, filters.length);
    }
}
