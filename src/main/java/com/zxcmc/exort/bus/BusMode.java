package com.zxcmc.exort.bus;

public enum BusMode {
    DISABLED,
    WHITELIST,
    BLACKLIST,
    ALL;

    public BusMode next() {
        return switch (this) {
            case DISABLED -> WHITELIST;
            case WHITELIST -> BLACKLIST;
            case BLACKLIST -> ALL;
            case ALL -> DISABLED;
        };
    }

    public static BusMode fromString(String raw) {
        if (raw == null) return null;
        try {
            return BusMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
