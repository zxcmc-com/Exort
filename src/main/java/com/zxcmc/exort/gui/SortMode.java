package com.zxcmc.exort.gui;

public enum SortMode {
    AMOUNT,
    NAME,
    ID,
    CATEGORY;

    public static SortMode fromString(String raw) {
        if (raw == null) return AMOUNT;
        for (SortMode mode : values()) {
            if (mode.name().equalsIgnoreCase(raw)) {
                return mode;
            }
        }
        return AMOUNT;
    }
}
